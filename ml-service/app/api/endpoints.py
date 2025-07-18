from fastapi import APIRouter, Depends
from .schemas import ForecastRequest, ForecastResponse
from ..services.model_service import ModelService
from ..models.demand_forecaster import DemandForecaster

router = APIRouter()

@router.post("/forecast", response_model=ForecastResponse)
async def get_forecast(request: ForecastRequest):
    """Получение прогноза спроса"""
    forecaster = DemandForecaster(request.product_id)

    # Если нужно переобучить модель
    if request.retrain:
        data_processor = DataProcessor()
        data = data_processor.get_training_data(request.product_id)
        forecaster.train(data, model_type=request.model_type)

    forecast = forecaster.forecast(periods=request.periods)
    return {
        "product_id": request.product_id,
        "forecast": forecast.to_dict(orient="records")
    }

@router.post("/train/{product_id}")
async def train_model(product_id: str, model_type: str = "prophet"):
    """Переобучение модели для конкретного продукта"""
    data_processor = DataProcessor()
    data = data_processor.get_training_data(product_id)

    forecaster = DemandForecaster(product_id)
    forecaster.train(data, model_type=model_type)

    return {"status": "success", "model_type": model_type}