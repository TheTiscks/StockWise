from kafka import KafkaConsumer
import json
import os
from .data_processor import DataProcessor

class KafkaDataCollector:
    def __init__(self):
        self.bootstrap_servers = os.getenv("KAFKA_BOOTSTRAP_SERVERS", "localhost:9092")
        self.topics = [
            "order-events",
            "inventory-updates"
        ]
        self.processor = DataProcessor()

    def start_consuming(self):
        consumer = KafkaConsumer(
            *self.topics,
            bootstrap_servers=self.bootstrap_servers,
            value_deserializer=lambda v: json.loads(v.decode('utf-8')),
            group_id="ml-service-group"
        )

        print("ML Consumer started. Waiting for messages...")
        for message in consumer:
            try:
                self.process_message(message)
            except Exception as e:
                print(f"Error processing message: {e}")

    def process_message(self, message):
        topic = message.topic
        data = message.value

        if topic == "order-events":
            if data['event_type'] == "ORDER_FULFILLED":
                self.processor.process_order_event(data)
        elif topic == "inventory-updates":
            self.processor.process_inventory_event(data)