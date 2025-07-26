from fastapi import FastAPI
from .api.endpoints import router as api_router
from .services.kafka_consumer import KafkaDataCollector
import threading
from .predict import DemandPredictor
from pydantic import BaseModel

app = FastAPI(
    title="StockWise ML Service",
    description="API для прогнозирования спроса",
    version="0.1.0"
)

# Подключаем API роуты
app.include_router(api_router, prefix="/api/ml")

@app.on_event("startup")
def start_kafka_consumer():
    """Запуск Kafka консьюмера в отдельном потоке"""
    consumer = KafkaDataCollector()
    thread = threading.Thread(target=consumer.start_consuming)
    thread.daemon = True
    thread.start()
    print("Kafka consumer started in background thread")



predictor = DemandPredictor()

class PredictionRequest(BaseModel):
    product_id: str
    features: dict

@app.post("/predict")
async def predict_demand(request: PredictionRequest):
    prediction = predictor.predict(request.product_id, request.features)
    return {"product_id": request.product_id, "prediction": prediction}

# Загрузка/обучение модели при старте
@app.on_event("startup")
async def startup_event():
    # Заглушка - в реальности загрузка из БД
    sample_data = pd.DataFrame({
        'day_of_week': [1, 2, 3, 4, 5],
        'month': [6, 6, 6, 6, 6],
        'prev_sales_1': [10, 12, 11, 15, 13],
        'prev_sales_7': [8, 9, 10, 11, 12],
        'sales': [12, 14, 13, 16, 15]
    })
    predictor.train(sample_data)