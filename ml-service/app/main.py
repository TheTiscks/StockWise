from fastapi import FastAPI
from .api.endpoints import router as api_router
from .services.kafka_consumer import KafkaDataCollector
import threading

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