# StockWise - Система управления запасами

## 📋 Описание проекта

StockWise - это высокопроизводительная микросервисная система управления запасами для розничной торговли/производства с прогнозированием спроса на основе исторических данных.

### 🎯 Ключевые функции

- **Реальное отслеживание остатков товаров** - мгновенное обновление данных о запасах
- **Управление поставщиками и заказами** - автоматизация процессов закупок
- **Прогнозирование спроса с использованием ML** - точные прогнозы на основе исторических данных
- **Автоматизация решений о пополнении запасов** - умная система пополнения
- **Визуализация данных через веб-интерфейс** - интуитивный дашборд

### 🏗️ Архитектура

```
┌─────────────────┐    ┌─────────────────┐    ┌─────────────────┐
│   Blazor UI     │    │  API Gateway    │    │   ML Service    │
│   (Frontend)    │◄──►│   (Nginx)       │◄──►│   (Python)      │
└─────────────────┘    └─────────────────┘    └─────────────────┘
                              │
                              ▼
┌─────────────────┐    ┌─────────────────┐    ┌─────────────────┐
│Inventory Service│    │Supplier Service │    │  Order Service  │
│   (Java)        │    │   (Java)        │    │   (Java)        │
│   Port: 8080    │    │   Port: 8081    │    │   Port: 8082    │
└─────────────────┘    └─────────────────┘    └─────────────────┘
        │                       │                       │
        ▼                       ▼                       ▼
┌─────────────────┐    ┌─────────────────┐    ┌─────────────────┐
│   PostgreSQL    │    │     Redis       │    │     Kafka       │
│   (Database)    │    │   (Cache)       │    │  (Message Bus)  │
└─────────────────┘    └─────────────────┘    └─────────────────┘
```

## 🚀 Быстрый старт

### Предварительные требования

- Docker и Docker Compose
- Java 17+
- Python 3.11+
- .NET 8.0+
- Git

### Установка и запуск

1. **Клонирование репозитория**
```bash
git clone https://github.com/your-org/stockwise.git
cd stockwise
```

2. **Настройка переменных окружения**
```bash
cp .env.example .env
# Отредактируйте .env файл с вашими настройками
```

3. **Запуск системы**
```bash
cd infrastructure
docker-compose up -d
```

4. **Проверка работоспособности**
```bash
# Проверка сервисов
curl http://localhost:8080/actuator/health  # Inventory Service
curl http://localhost:8081/actuator/health  # Supplier Service
curl http://localhost:8082/actuator/health  # Order Service
curl http://localhost:5000/health           # ML Service
curl http://localhost:7000                  # Blazor UI
```

## 📚 Документация по API

### Inventory Service API

#### Получение информации о товаре
```http
GET /api/inventory/{productId}
```

#### Корректировка запасов
```http
POST /api/inventory/adjust
Content-Type: application/json

{
  "productId": "uuid",
  "delta": 10,
  "reason": "MANUAL_ADJUSTMENT"
}
```

#### Создание заказа на пополнение
```http
POST /api/inventory/replenish
Content-Type: application/json

{
  "productId": "uuid",
  "quantity": 100
}
```

#### Генерация отчёта
```http
GET /api/inventory/reports/demand?productId={uuid}&days=30
```

### Supplier Service API

#### Создание поставщика
```http
POST /api/suppliers
Content-Type: application/json

{
  "name": "ООО Поставщик",
  "contactEmail": "supplier@example.com",
  "phoneNumber": "+7-999-123-45-67",
  "address": "г. Москва, ул. Примерная, 1"
}
```

#### Получение списка поставщиков
```http
GET /api/suppliers?page=0&size=20
```

#### Добавление контракта
```http
POST /api/suppliers/{supplierId}/contracts
Content-Type: application/json

{
  "contractNumber": "CONTRACT-001",
  "startDate": "2024-01-01",
  "endDate": "2024-12-31",
  "terms": 1000.00,
  "deliveryDays": 7,
  "paymentConditions": "30 дней"
}
```

#### Поиск поставщиков для продукта
```http
GET /api/suppliers/available-for-product?productId={uuid}
```

#### Получение лучшего контракта
```http
GET /api/suppliers/best-contract?productId={uuid}&quantity=100
```

#### Автоматическое создание заказа
```http
POST /api/suppliers/auto-order?productId={uuid}&quantity=100
```

### Order Service API

#### Создание заказа
```http
POST /api/orders
Content-Type: application/json

{
  "supplierId": 1,
  "contractId": 1,
  "productId": "uuid",
  "quantity": 100,
  "unitPrice": 10.50
}
```

#### Получение заказа по номеру
```http
GET /api/orders/number/{orderNumber}
```

#### Получение всех заказов
```http
GET /api/orders?page=0&size=20
```

#### Обновление статуса заказа
```http
POST /api/orders/{id}/confirm
POST /api/orders/{id}/process
POST /api/orders/{id}/ship
POST /api/orders/{id}/deliver
POST /api/orders/{id}/cancel
```

#### Получение заказов по фильтрам
```http
GET /api/orders/supplier/{supplierId}
GET /api/orders/status/{status}
GET /api/orders/product/{productId}
GET /api/orders/pending?page=0&size=20
GET /api/orders/overdue
```

#### Статистика заказов
```http
GET /api/orders/stats
```

### ML Service API

#### Прогнозирование спроса
```http
POST /api/ml/predict
Content-Type: application/json

{
  "productId": "uuid",
  "features": {
    "day_of_week": 1,
    "month": 6,
    "prev_sales": [10, 12, 11, 15, 13]
  }
}
```

#### Обучение модели
```http
POST /api/ml/train
Content-Type: application/json

{
  "productId": "uuid",
  "modelType": "prophet"
}
```

## 🔧 Конфигурация

### Переменные окружения

#### Inventory Service
```bash
SPRING_DATASOURCE_URL=jdbc:postgresql://postgres:5432/inventory
SPRING_DATASOURCE_USERNAME=admin
SPRING_DATASOURCE_PASSWORD=password
SPRING_KAFKA_BOOTSTRAP_SERVERS=kafka:9092
SPRING_REDIS_HOST=redis
SPRING_REDIS_PORT=6379
```

#### Supplier Service
```bash
SPRING_DATASOURCE_URL=jdbc:postgresql://postgres:5432/inventory
SPRING_DATASOURCE_USERNAME=admin
SPRING_DATASOURCE_PASSWORD=password
SPRING_KAFKA_BOOTSTRAP_SERVERS=kafka:9092
SPRING_REDIS_HOST=redis
SPRING_REDIS_PORT=6379
```

#### Order Service
```bash
SPRING_DATASOURCE_URL=jdbc:postgresql://postgres:5432/inventory
SPRING_DATASOURCE_USERNAME=admin
SPRING_DATASOURCE_PASSWORD=password
SPRING_KAFKA_BOOTSTRAP_SERVERS=kafka:9092
SPRING_REDIS_HOST=redis
SPRING_REDIS_PORT=6379
```

#### ML Service
```bash
DATABASE_URL=postgresql://admin:password@postgres:5432/inventory
KAFKA_BROKER=kafka:9092
MODELS_DIR=/app/models
```

#### Blazor UI
```bash
API_BASE_URL=http://localhost:8080
ML_API_URL=http://localhost:5000
```

## 🧪 Тестирование

### Запуск тестов

```bash
# Java тесты
cd inventory-service
mvn test

cd ../supplier-service
mvn test

cd ../order-service
mvn test

# Python тесты
cd ml-service
pytest tests/

# .NET тесты
cd blazor-ui
dotnet test
```

### Интеграционные тесты

```bash
cd infrastructure
docker-compose -f docker-compose.test.yml up -d
./run-integration-tests.sh
docker-compose -f docker-compose.test.yml down
```

## 📊 Мониторинг

### Метрики

- **Производительность**: Время отклика API, пропускная способность
- **Надёжность**: Доступность сервисов, количество ошибок
- **Бизнес-метрики**: Точность прогнозов, эффективность пополнения

### Логирование

Все сервисы используют структурированное логирование в формате JSON:

```json
{
  "timestamp": "2024-01-15T10:30:00Z",
  "level": "INFO",
  "service": "inventory-service",
  "message": "Stock adjusted successfully",
  "productId": "uuid",
  "delta": 10
}
```

## 🔒 Безопасность

### Аутентификация и авторизация

- OAuth2/JWT токены
- Ролевая модель доступа (админ, менеджер, аналитик)
- Шифрование чувствительных данных

### Защита API

- Rate limiting
- Input validation
- SQL injection protection
- XSS protection

## 🚀 Деплой

### Docker Compose (разработка)

```bash
docker-compose up -d
```

### Kubernetes (продакшн)

```bash
# Установка Helm чартов
helm install stockwise ./helm/stockwise

# Обновление
helm upgrade stockwise ./helm/stockwise

# Удаление
helm uninstall stockwise
```

### CI/CD

Система использует GitHub Actions для автоматического деплоя:

1. **Тестирование** - автоматические тесты при каждом PR
2. **Сборка** - создание Docker образов
3. **Деплой в staging** - автоматический деплой в staging окружение
4. **Деплой в production** - ручное подтверждение для production

## 🤝 Вклад в проект

### Структура проекта

```
stockwise/
├── blazor-ui/           # Frontend (Blazor WebAssembly)
├── inventory-service/    # Сервис инвентаря (Java)
├── supplier-service/     # Сервис поставщиков (Java)
├── order-service/        # Сервис заказов (Java)
├── ml-service/          # ML сервис (Python)
├── infrastructure/      # Docker, K8s конфигурации
├── docs/               # Документация
└── tests/              # Интеграционные тесты
```

### Правила разработки

1. **Ветвление**: feature/feature-name, bugfix/bug-description
2. **Коммиты**: Conventional Commits (feat:, fix:, docs:, etc.)
3. **Code Review**: обязательный для всех PR
4. **Тестирование**: минимум 80% покрытия кода

## 📞 Поддержка

- **Документация**: [docs/](docs/)
- **Issues**: [GitHub Issues](https://github.com/your-org/stockwise/issues)
- **Discussions**: [GitHub Discussions](https://github.com/your-org/stockwise/discussions)

## 📄 Лицензия

MIT License - см. [LICENSE](LICENSE) файл для деталей.




