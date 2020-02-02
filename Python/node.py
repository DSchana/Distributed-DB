#node.py

import socket
import threading

#AF_INET says we're using IPV4
#SOCK_STREAM says we're using TCP
sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)

#binding socket to a port
#setting address to 0.0.0.0 makes it available to any IP configured on the server
sock.bind(('0.0.0.0',8000))


sock.listen(1)

connections=[]

def handler(c,a):
    global connections
    while True:
        #data received and max ammount in bytes
        data = c.recv(1024)
        print(data)
        for connection in connections:
            connection.send(bytes(data))
        if not data:
            connections.remove(c)
            c.close()
            break

while True:
    #c is the connection, a is client's address
    c, a = sock.accept()
    cThread = threading.Thread(target=handler, args=(c,a))
    cThread.daemon = True
    cThread.start()
    connections.append(c)
    print(connections)