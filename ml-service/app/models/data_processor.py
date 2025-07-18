import pandas as pd
from datetime import datetime
from sqlalchemy import create_engine
import os

class DataProcessor:
    def __init__(self):
        self.db_engine = create_engine(os.getenv("DATABASE_URL", "postgresql://user:pass@localhost:5432/ml_data"))
        self._create_tables()

    def _create_tables(self):
        # Создаем таблицы, если их нет
        with self.db_engine.connect() as conn:
            conn.execute("""
            CREATE TABLE IF NOT EXISTS sales_data (
                id SERIAL PRIMARY KEY,
                product_id UUID NOT NULL,
                sale_date DATE NOT NULL,
                quantity INTEGER NOT NULL,
                price NUMERIC
            );
            """)
            conn.execute("""
            CREATE TABLE IF NOT EXISTS inventory_data (
                id SERIAL PRIMARY KEY,
                product_id UUID NOT NULL,
                record_date TIMESTAMP NOT NULL,
                quantity INTEGER NOT NULL
            );
            """)

    def process_order_event(self, event):
        # Преобразуем событие в DataFrame
        df = pd.DataFrame([{
            'product_id': item['product_id'],
            'sale_date': datetime.utcnow().date(),
            'quantity': item['quantity'],
            'price': item.get('price', None)
        } for item in event['items']])

        # Сохраняем в БД
        df.to_sql('sales_data', self.db_engine, if_exists='append', index=False)
        print(f"Processed {len(df)} sales records")

    def process_inventory_event(self, event):
        # Создаем запись об изменении инвентаря
        record = {
            'product_id': event['product_id'],
            'record_date': datetime.utcnow(),
            'quantity': event['quantity']
        }

        # Сохраняем в БД
        pd.DataFrame([record]).to_sql(
            'inventory_data',
            self.db_engine,
            if_exists='append',
            index=False
        )
        print(f"Processed inventory update for {event['product_id']}")

    def get_training_data(self, product_id):
        # Получаем данные для обучения
        query = f"""
        SELECT
            s.sale_date AS date,
            s.quantity,
            s.price,
            i.quantity AS inventory
        FROM sales_data s
        LEFT JOIN inventory_data i
            ON s.product_id = i.product_id
            AND i.record_date = (
                SELECT MAX(record_date)
                FROM inventory_data
                WHERE product_id = s.product_id
                AND record_date <= s.sale_date
            )
        WHERE s.product_id = '{product_id}'
        ORDER BY s.sale_date
        """

        return pd.read_sql_query(query, self.db_engine)