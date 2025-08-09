import pandas as pd
import numpy as np
from prophet import Prophet
from statsmodels.tsa.arima.model import ARIMA
from statsmodels.tsa.stattools import adfuller
from sklearn.metrics import mean_absolute_error, mean_squared_error
import joblib
import os
import logging
from typing import Dict, List, Optional, Tuple
from datetime import datetime, timedelta

logger = logging.getLogger(__name__)

class DemandForecaster:
    def __init__(self, product_id: str):
        self.product_id = product_id
        self.models_dir = os.getenv("MODELS_DIR", "ml-service/models")
        self.model_path = f"{self.models_dir}/model_{product_id}.joblib"
        self.model = None
        self.model_type = None
        self.metrics = {}
        
    def train(self, data: pd.DataFrame, model_type: str = "prophet") -> Dict:
        """Обучение модели прогнозирования"""
        if data.empty:
            raise ValueError("Empty dataset provided")
        
        try:
            if model_type == "prophet":
                return self._train_prophet(data)
            elif model_type == "arima":
                return self._train_arima(data)
            elif model_type == "ensemble":
                return self._train_ensemble(data)
            else:
                raise ValueError(f"Unsupported model type: {model_type}")
                
        except Exception as e:
            logger.error(f"Error training {model_type} model for product {self.product_id}: {e}")
            raise
    
    def _train_prophet(self, data: pd.DataFrame) -> Dict:
        """Обучение модели Prophet"""
        # Подготовка данных для Prophet
        df = data[['date', 'sales']].copy()
        df.columns = ['ds', 'y']
        df['ds'] = pd.to_datetime(df['ds'])
        
        # Создание и настройка модели
        model = Prophet(
            daily_seasonality=False,
            weekly_seasonality=True,
            yearly_seasonality=True,
            changepoint_prior_scale=0.05,
            seasonality_prior_scale=10.0
        )
        
        # Добавление праздников (для России)
        model.add_country_holidays(country_name='RU')
        
        # Обучение модели
        model.fit(df)
        
        self.model = model
        self.model_type = "prophet"
        
        # Оценка качества
        metrics = self._evaluate_prophet_model(model, df)
        
        # Сохранение модели
        self.save_model()
        
        return {
            'model_type': 'prophet',
            'metrics': metrics,
            'status': 'success'
        }
    
    def _train_arima(self, data: pd.DataFrame) -> Dict:
        """Обучение модели ARIMA"""
        # Подготовка данных
        ts_data = data.set_index('date')['sales'].sort_index()
        
        # Проверка стационарности
        if not self._is_stationary(ts_data):
            ts_data = self._make_stationary(ts_data)
        
        # Определение параметров ARIMA
        p, d, q = self._find_best_arima_params(ts_data)
        
        # Обучение модели
        model = ARIMA(ts_data, order=(p, d, q))
        fitted_model = model.fit()
        
        self.model = fitted_model
        self.model_type = "arima"
        
        # Оценка качества
        metrics = self._evaluate_arima_model(fitted_model, ts_data)
        
        # Сохранение модели
        self.save_model()
        
        return {
            'model_type': 'arima',
            'parameters': {'p': p, 'd': d, 'q': q},
            'metrics': metrics,
            'status': 'success'
        }
    
    def _train_ensemble(self, data: pd.DataFrame) -> Dict:
        """Обучение ансамблевой модели"""
        results = {}
        
        # Обучаем Prophet
        try:
            prophet_result = self._train_prophet(data)
            results['prophet'] = prophet_result
        except Exception as e:
            logger.warning(f"Prophet training failed: {e}")
        
        # Обучаем ARIMA
        try:
            arima_result = self._train_arima(data)
            results['arima'] = arima_result
        except Exception as e:
            logger.warning(f"ARIMA training failed: {e}")
        
        self.model_type = "ensemble"
        self.model = results
        
        return {
            'model_type': 'ensemble',
            'models': results,
            'status': 'success'
        }
    
    def predict(self, days_ahead: int = 30) -> pd.DataFrame:
        """Прогнозирование спроса"""
        if not self.model:
            self.load_model()
        
        if not self.model:
            raise ValueError("No trained model available")
        
        try:
            if self.model_type == "prophet":
                return self._predict_prophet(days_ahead)
            elif self.model_type == "arima":
                return self._predict_arima(days_ahead)
            elif self.model_type == "ensemble":
                return self._predict_ensemble(days_ahead)
            else:
                raise ValueError(f"Unknown model type: {self.model_type}")
                
        except Exception as e:
            logger.error(f"Error making prediction: {e}")
            raise
    
    def _predict_prophet(self, days_ahead: int) -> pd.DataFrame:
        """Прогнозирование с Prophet"""
        future = self.model.make_future_dataframe(periods=days_ahead)
        forecast = self.model.predict(future)
        
        return forecast[['ds', 'yhat', 'yhat_lower', 'yhat_upper']].tail(days_ahead)
    
    def _predict_arima(self, days_ahead: int) -> pd.DataFrame:
        """Прогнозирование с ARIMA"""
        forecast = self.model.forecast(steps=days_ahead)
        
        dates = pd.date_range(
            start=datetime.now() + timedelta(days=1),
            periods=days_ahead,
            freq='D'
        )
        
        return pd.DataFrame({
            'ds': dates,
            'yhat': forecast.values,
            'yhat_lower': forecast.values * 0.9,
            'yhat_upper': forecast.values * 1.1
        })
    
    def _predict_ensemble(self, days_ahead: int) -> pd.DataFrame:
        """Прогнозирование с ансамблем"""
        predictions = []
        
        for model_name, model_result in self.model.items():
            if model_result['status'] == 'success':
                # Временно устанавливаем модель для прогноза
                temp_model = self.model[model_name].get('model')
                if temp_model:
                    if model_name == 'prophet':
                        pred = self._predict_prophet(days_ahead)
                    elif model_name == 'arima':
                        pred = self._predict_arima(days_ahead)
                    predictions.append(pred['yhat'])
        
        if not predictions:
            raise ValueError("No valid models in ensemble")
        
        # Усреднение прогнозов
        ensemble_pred = np.mean(predictions, axis=0)
        
        dates = pd.date_range(
            start=datetime.now() + timedelta(days=1),
            periods=days_ahead,
            freq='D'
        )
        
        return pd.DataFrame({
            'ds': dates,
            'yhat': ensemble_pred,
            'yhat_lower': ensemble_pred * 0.9,
            'yhat_upper': ensemble_pred * 1.1
        })
    
    def _is_stationary(self, ts_data: pd.Series) -> bool:
        """Проверка стационарности временного ряда"""
        result = adfuller(ts_data.dropna())
        return result[1] < 0.05
    
    def _make_stationary(self, ts_data: pd.Series) -> pd.Series:
        """Приведение к стационарности"""
        return ts_data.diff().dropna()
    
    def _find_best_arima_params(self, ts_data: pd.Series) -> Tuple[int, int, int]:
        """Поиск лучших параметров ARIMA"""
        best_aic = float('inf')
        best_params = (1, 1, 1)
        
        for p in range(0, 3):
            for d in range(0, 2):
                for q in range(0, 3):
                    try:
                        model = ARIMA(ts_data, order=(p, d, q))
                        fitted_model = model.fit()
                        if fitted_model.aic < best_aic:
                            best_aic = fitted_model.aic
                            best_params = (p, d, q)
                    except:
                        continue
        
        return best_params
    
    def _evaluate_prophet_model(self, model: Prophet, data: pd.DataFrame) -> Dict:
        """Оценка качества модели Prophet"""
        # Кросс-валидация
        from prophet.diagnostics import cross_validation, performance_metrics
        
        df_cv = cross_validation(model, initial='180 days', period='30 days', horizon='90 days')
        df_p = performance_metrics(df_cv)
        
        return {
            'mae': df_p['mae'].mean(),
            'rmse': df_p['rmse'].mean(),
            'mape': df_p['mape'].mean()
        }
    
    def _evaluate_arima_model(self, model, ts_data: pd.Series) -> Dict:
        """Оценка качества модели ARIMA"""
        # Прогноз на тестовом наборе
        train_size = int(len(ts_data) * 0.8)
        train = ts_data[:train_size]
        test = ts_data[train_size:]
        
        predictions = model.forecast(steps=len(test))
        
        mae = mean_absolute_error(test, predictions)
        rmse = np.sqrt(mean_squared_error(test, predictions))
        
        return {
            'mae': mae,
            'rmse': rmse,
            'aic': model.aic
        }
    
    def save_model(self) -> None:
        """Сохранение модели"""
        os.makedirs(self.models_dir, exist_ok=True)
        joblib.dump({
            'model': self.model,
            'model_type': self.model_type,
            'metrics': self.metrics
        }, self.model_path)
        logger.info(f"Model saved to {self.model_path}")
    
    def load_model(self) -> None:
        """Загрузка модели"""
        if os.path.exists(self.model_path):
            model_data = joblib.load(self.model_path)
            self.model = model_data['model']
            self.model_type = model_data['model_type']
            self.metrics = model_data.get('metrics', {})
            logger.info(f"Model loaded from {self.model_path}")
        else:
            logger.warning(f"No saved model found at {self.model_path}")
    
    def get_model_info(self) -> Dict:
        """Получение информации о модели"""
        return {
            'product_id': self.product_id,
            'model_type': self.model_type,
            'metrics': self.metrics,
            'last_updated': datetime.now().isoformat()
        }