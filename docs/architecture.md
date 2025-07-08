graph TD
  A[Клиент] --> B[API Gateway]
  B --> C[Сервис инвентаря]
  B --> D[Сервис заказов]
  B --> E[Сервис поставщиков]
  C --> F[PostgreSQL] 
  D --> G[Kafka]
  E --> H[Redis]
  G --> I[ML Service]

