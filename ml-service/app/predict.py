import pandas as pd
from sklearn.ensemble import RandomForestRegressor
from joblib import dump, load
import os

MODEL_PATH = "models/inventory_model.joblib"

class DemandPredictor:
    def __init__(self):
        self.model = None
        self.load_model()

    def load_model(self):
        if os.path.exists(MODEL_PATH):
            self.model = load(MODEL_PATH)
        else:
            self.model = RandomForestRegressor(n_estimators=100, random_state=42)

    def train(self, data: pd.DataFrame):
        """Обучение модели на исторических данных"""
        X = data[['day_of_week', 'month', 'prev_sales_1', 'prev_sales_7']]
        y = data['sales']
        self.model.fit(X, y)
        dump(self.model, MODEL_PATH)

    def predict(self, product_id: str, features: dict) -> float:
        """Прогнозирование спроса"""
        if not self.model:
            self.load_model()

        # Преобразование признаков
        input_data = pd.DataFrame([{
            'day_of_week': features['day_of_week'],
            'month': features['month'],
            'prev_sales_1': features['prev_sales'][-1] if features['prev_sales'] else 0,
            'prev_sales_7': sum(features['prev_sales'][-7:])/7 if features['prev_sales'] else 0
        }])

        return max(0, self.model.predict(input_data)[0])