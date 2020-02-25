from client import *

client = Client('localhost', 8001)

print(client.create())

print(client.insert("john", 5))

print(client.insert("tom", 1))

print(client.get("tom"))

print(client.get("john"))

print(client.delete("tom"))

print(client.get("tom"))

print(client.upsert("tom", 1))

print(client.find("tom"))

print(client.count())

print(client.clear())

print(client.count())