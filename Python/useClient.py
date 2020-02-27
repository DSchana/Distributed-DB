from client import *

client1 = Client('localhost', 8001)
client2 = Client('localhost', 8001)

print("client1.create(): " + str(client1.create()))
print("client2.create(): " + str(client2.create()))

print("client1.insert('john', 5): " + str(client1.insert("john", 5)))
print("client2.insert('john', 5): " + str(client2.insert("john", 5)))

print("client1.insert('tom', 1): " + str(client1.insert("tom", 1)))

print("client1.get('tom'): " + str(client1.get("tom")))

print("client1.get('john'): " + str(client1.get("john")))

print("client1.delete('tom'): " + client1.delete("tom"))

print("client1.get('tom'): " + str(client1.get("tom")))

print("client1.upsert('tom', 1): " + client1.upsert("tom", 1))

print("client1.find('tom'): " + str(client1.find("tom")))

print("client1.count(): " + str(client1.count()))

print("client1.clear(): " + client1.clear())

print("client1.count(): " + str(client1.count()))

print("client2.get('john'): " + str(client2.get("john")))
print("client2.count(): " + str(client2.count())) 