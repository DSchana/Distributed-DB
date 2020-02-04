import socket
import threading
import sys

class Client:
    sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)

    def sendMsg(self):
        while True:
            self.sock.send(bytes(input(""), 'utf-8'))
    
    def __init__(self, address):
        self.sock.connect((address, 8001))

        #iThread= input thread
        iThread = threading.Thread(target=self.sendMsg)
        iThread.daemon=True
        iThread.start()

        while True:
            data=self.sock.recv(1024)
            if not data:
                break
            print(str(data,'utf-8'))

client = Client(sys.argv[1])
