from client import *

client = Client('localhost', 8001)

print("client.create(): " + client.create())

print("client.insert('john', 5): " + client.insert("john", 5))

print("client.insert('tom', 1): " + client.insert("tom", 1))

print("client.get('tom'): " + str(client.get("tom")))

print("client.get('john'): " + str(client.get("john")))

print("client.delete('tom'): " + client.delete("tom"))

print("client.get('tom'): " + str(client.get("tom")))

print("client.upsert('tom', 1): " + client.upsert("tom", 1))

print("client.find('tom'): " + str(client.find("tom")))

print("client.count(): " + str(client.count()))

print("client.clear(): " + client.clear())

print("client.count(): " + str(client.count()))