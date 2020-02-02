#node.py

import socket
import threading
from KeyVal import *

class Server:
    #AF_INET says we're using IPV4
    #SOCK_STREAM says we're using TCP
    sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    connections=[]
    keyval = KeyVal()

    def __init__(self):
        #binding socket to a port
        #setting address to 0.0.0.0 makes it available to any IP configured on the server
        self.sock.bind(('0.0.0.0',8000))
        self.sock.listen(1)

    def convert(self, s):
        arr=s.split()
        command=arr[0]+'('
        for i in range(1,len(arr)):
            try:
                int(arr[i])
                command+=arr[i]
            except:
                command+="'"+arr[i]+"'"
            if i<len(arr)-1:
                command+=','
        command+=')'
        print('self.keyval.'+command)
        try:
            return eval('self.keyval.'+command)
        except:
            return 'Error'

        


    def handler(self,c,a):
        while True:
            #data received and max ammount in bytes
            data = c.recv(1024)
            text = str(data, 'utf-8')
            result = self.convert(text)
            #print(str(data,'utf-8'))
            c.send(bytes(result.encode()))
            if not data:
                self.connections.remove(c)
                c.close()
                print(str(a[0]+':'+a[1]+" Disconnected"))
                break
    def run(self):
        while True:
            #c is the connection, a is client's address
            c, a = self.sock.accept()
            cThread = threading.Thread(target=self.handler, args=(c,a))
            cThread.daemon = True
            cThread.start()
            self.connections.append(c)
            print(str(a[0])+':'+str(a[1])+" Connected")


server = Server()

server.run()