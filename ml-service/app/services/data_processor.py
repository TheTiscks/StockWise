import pandas as pd
import numpy as np
from datetime import datetime, timedelta
from typing import Dict, List, Optional
import logging
from sqlalchemy import create_engine, text
import os

logger = logging.getLogger(__name__)

class DataProcessor:
    def __init__(self):
        self.engine = create_engine(os.getenv('DATABASE_URL', 'postgresql://admin:password@localhost:5432/inventory'))
        self.processed_data = {}
    
    def extract_historical_data(self, product_id: str, days_back: int = 365) -> pd.DataFrame:
        """Извлечение исторических данных из БД"""
        try:
            query = text("""
                SELECT 
                    DATE(created_at) as date,
                    product_id,
                    SUM(CASE WHEN action = 'SALE' THEN ABS(delta) ELSE 0 END) as sales,
                    SUM(CASE WHEN action = 'PURCHASE' THEN ABS(delta) ELSE 0 END) as purchases,
                    COUNT(*) as transactions
                FROM inventory_history 
                WHERE product_id = :product_id 
                AND created_at >= :start_date
                GROUP BY DATE(created_at), product_id
                ORDER BY date
            """)
            
            start_date = datetime.now() - timedelta(days=days_back)
            
            df = pd.read_sql(
                query, 
                self.engine, 
                params={'product_id': product_id, 'start_date': start_date}
            )
            
            logger.info(f"Extracted {len(df)} records for product {product_id}")
            return df
            
        except Exception as e:
            logger.error(f"Error extracting data for product {product_id}: {e}")
            return pd.DataFrame()
    
    def transform_data(self, df: pd.DataFrame) -> pd.DataFrame:
        """Трансформация данных для ML моделей"""
        if df.empty:
            return df
        
        # Добавляем временные признаки
        df['date'] = pd.to_datetime(df['date'])
        df['day_of_week'] = df['date'].dt.dayofweek
        df['month'] = df['date'].dt.month
        df['quarter'] = df['date'].dt.quarter
        df['year'] = df['date'].dt.year
        
        # Скользящие средние
        df['sales_ma_7'] = df['sales'].rolling(window=7, min_periods=1).mean()
        df['sales_ma_30'] = df['sales'].rolling(window=30, min_periods=1).mean()
        
        # Лаговые признаки
        df['sales_lag_1'] = df['sales'].shift(1)
        df['sales_lag_7'] = df['sales'].shift(7)
        df['sales_lag_30'] = df['sales'].shift(30)
        
        # Сезонные признаки
        df['is_weekend'] = df['day_of_week'].isin([5, 6]).astype(int)
        df['is_month_start'] = df['date'].dt.is_month_start.astype(int)
        df['is_month_end'] = df['date'].dt.is_month_end.astype(int)
        
        # Удаляем NaN значения
        df = df.dropna()
        
        return df
    
    def load_processed_data(self, product_id: str, df: pd.DataFrame) -> None:
        """Загрузка обработанных данных в кэш"""
        self.processed_data[product_id] = df
        logger.info(f"Loaded processed data for product {product_id}: {len(df)} records")
    
    def process_order_event(self, event_data: Dict) -> None:
        """Обработка события заказа"""
        try:
            product_id = event_data.get('product_id')
            quantity = event_data.get('quantity', 0)
            event_type = event_data.get('event_type')
            
            # Сохраняем событие в БД
            self._save_event_to_db(product_id, quantity, event_type)
            
            # Обновляем кэш данных
            if product_id in self.processed_data:
                self._update_cached_data(product_id, quantity, event_type)
                
        except Exception as e:
            logger.error(f"Error processing order event: {e}")
    
    def process_inventory_event(self, event_data: Dict) -> None:
        """Обработка события инвентаря"""
        try:
            product_id = event_data.get('product_id')
            delta = event_data.get('delta', 0)
            reason = event_data.get('reason', 'UNKNOWN')
            
            # Определяем тип события
            event_type = 'SALE' if delta < 0 else 'PURCHASE'
            
            # Сохраняем событие в БД
            self._save_event_to_db(product_id, abs(delta), event_type, reason)
            
        except Exception as e:
            logger.error(f"Error processing inventory event: {e}")
    
    def _save_event_to_db(self, product_id: str, quantity: int, event_type: str, reason: str = None) -> None:
        """Сохранение события в БД"""
        try:
            query = text("""
                INSERT INTO inventory_history (product_id, action, delta, reason, created_at)
                VALUES (:product_id, :action, :delta, :reason, :created_at)
            """)
            
            with self.engine.connect() as conn:
                conn.execute(query, {
                    'product_id': product_id,
                    'action': event_type,
                    'delta': quantity,
                    'reason': reason,
                    'created_at': datetime.now()
                })
                conn.commit()
                
        except Exception as e:
            logger.error(f"Error saving event to DB: {e}")
    
    def _update_cached_data(self, product_id: str, quantity: int, event_type: str) -> None:
        """Обновление кэшированных данных"""
        if product_id in self.processed_data:
            df = self.processed_data[product_id]
            today = datetime.now().date()
            
            # Находим или создаем запись для сегодня
            if today in df['date'].dt.date.values:
                mask = df['date'].dt.date == today
                if event_type == 'SALE':
                    df.loc[mask, 'sales'] += quantity
                else:
                    df.loc[mask, 'purchases'] += quantity
                df.loc[mask, 'transactions'] += 1
            else:
                # Создаем новую запись
                new_row = pd.DataFrame([{
                    'date': today,
                    'product_id': product_id,
                    'sales': quantity if event_type == 'SALE' else 0,
                    'purchases': quantity if event_type == 'PURCHASE' else 0,
                    'transactions': 1
                }])
                df = pd.concat([df, new_row], ignore_index=True)
            
            # Пересчитываем признаки
            df = self.transform_data(df)
            self.processed_data[product_id] = df
    
    def get_features_for_prediction(self, product_id: str) -> Dict:
        """Получение признаков для прогнозирования"""
        if product_id not in self.processed_data:
            # Загружаем данные если их нет в кэше
            df = self.extract_historical_data(product_id)
            if not df.empty:
                df = self.transform_data(df)
                self.load_processed_data(product_id, df)
        
        if product_id in self.processed_data:
            df = self.processed_data[product_id]
            if not df.empty:
                latest = df.iloc[-1]
                return {
                    'day_of_week': latest['day_of_week'],
                    'month': latest['month'],
                    'quarter': latest['quarter'],
                    'sales_ma_7': latest['sales_ma_7'],
                    'sales_ma_30': latest['sales_ma_30'],
                    'sales_lag_1': latest['sales_lag_1'],
                    'sales_lag_7': latest['sales_lag_7'],
                    'is_weekend': latest['is_weekend'],
                    'is_month_start': latest['is_month_start'],
                    'is_month_end': latest['is_month_end']
                }
        
        # Возвращаем дефолтные значения
        return {
            'day_of_week': datetime.now().weekday(),
            'month': datetime.now().month,
            'quarter': (datetime.now().month - 1) // 3 + 1,
            'sales_ma_7': 0,
            'sales_ma_30': 0,
            'sales_lag_1': 0,
            'sales_lag_7': 0,
            'is_weekend': 1 if datetime.now().weekday() >= 5 else 0,
            'is_month_start': 1 if datetime.now().day == 1 else 0,
            'is_month_end': 1 if datetime.now().day >= 28 else 0
        }



