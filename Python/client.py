import socket
import threading
import sys
import json


class Client:
    sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)

    def sendMsg(self):
        while True:
            userCommand = input("Enter command, type exit to exit: ")
            self.sock.send(bytes(userCommand, 'utf-8'))

            if userCommand == 'exit':
                print('Attemping to close')
                self.sock.shutdown(1)
                self.sock.close()
                break

            # length = len(userInput)
            # lengthHeader = bytearray('\x00\x00\x00')
            # lengthHeader.append(bytes(length))

            # finalMessage = bytearray()
            # finalMessage.append(lengthHeader)
            # finalMessage.append(bytes(userInput, 'utf-8'))
            # message = userInput.len(s.encode('utf-8'))
        sys.exit(0)

    def __init__(self, address):
        self.sock.connect((address, 8002))

        # iThread= input thread
        iThread = threading.Thread(target=self.sendMsg)
        iThread.daemon = True
        iThread.start()

        while True:
            data = self.sock.recv(1024)
            if not data:
                break
            print(str(data, 'utf-8'))


client = Client(sys.argv[1])
