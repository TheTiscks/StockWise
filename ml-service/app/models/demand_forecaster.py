import pandas as pd
from prophet import Prophet
from statsmodels.tsa.arima.model import ARIMA
import numpy as np
import joblib
import os

MODELS_DIR = os.getenv("MODELS_DIR", "ml-service/models")

class DemandForecaster:
    def __init__(self, product_id):
        self.product_id = product_id
        self.model_path = f"{MODELS_DIR}/model_{product_id}.joblib"
        self.model = None

    def train(self, data, model_type="prophet"):
        """Обучение модели прогнозирования"""
        if model_type == "prophet":
            self._train_prophet(data)
        elif model_type == "arima":
            self._train_arima(data)

        self.save_model()

    def _train_prophet(self, data):
        # Подготовка данных для Prophet
        df = data[['date', 'quantity']].copy()
        df.columns = ['ds', 'y']

        # Создание и обучение модели
        model = Prophet(
            daily_seasonality=False,
            weekly_seasonality=True,
            yearly_seasonality=True
        )
        model.add_country_holidays(country_name='RU')
        model.fit(df)

        self.model = model
        self.model_type = "prophet"

    def _train_arima(self, data):
        # Подготовка данных для ARIMA
        series = data.set_index('date')['quantity']

        # Автоматический подбор параметров ARIMA
        best_aic = np.inf
        best_order = None
        best_model = None

        # Перебор параметров (можно расширить)
        for p in range(0, 3):
            for d in range(0, 2):
                for q in range(0, 3):
                    try:
                        model = ARIMA(series, order=(p, d, q))
                        model_fit = model.fit()
                        if model_fit.aic < best_aic:
                            best_aic = model_fit.aic
                            best_order = (p, d, q)
                            best_model = model_fit
                    except:
                        continue

        self.model = best_model
        self.model_type = "arima"
        self.model_order = best_order

    def forecast(self, periods=30):
        """Прогнозирование спроса"""
        if not self.model:
            self.load_model()

        if self.model_type == "prophet":
            future = self.model.make_future_dataframe(periods=periods)
            forecast = self.model.predict(future)
            return forecast[['ds', 'yhat', 'yhat_lower', 'yhat_upper']].tail(periods)

        elif self.model_type == "arima":
            forecast = self.model.forecast(steps=periods)
            return pd.DataFrame({
                'date': pd.date_range(start=pd.Timestamp.now(), periods=periods),
                'yhat': forecast.values,
                'yhat_lower': forecast.values * 0.9,
                'yhat_upper': forecast.values * 1.1
            })

    def save_model(self):
        """Сохранение модели на диск"""
        joblib.dump({
            'model': self.model,
            'model_type': self.model_type,
            'order': getattr(self, 'model_order', None)
        }, self.model_path)

    def load_model(self):
        """Загрузка модели с диска"""
        if os.path.exists(self.model_path):
            model_data = joblib.load(self.model_path)
            self.model = model_data['model']
            self.model_type = model_data['model_type']
            if 'order' in model_data:
                self.model_order = model_data['order']