import socket
import threading
from KeyVal import *
import sys
import json

class Server:
    def __init__(self, host, port):
        self.host = host
        self.port = port
<<<<<<< HEAD
        self.keyvals = {}
        self.lock = threading.Lock()
        self.IDs = []
        self.sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        self.sock.bind((self.host, self.port))
        self.sock.listen(1)
        self.selfID = None
        self.selectInput = [self.sock]
        self.selectOutput = []

        self.selfID = 1
        self.nonCentralNodes = []

        self.nodeCreateSock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        self.nodeCreateSock.bind((self.host, 8002))
        self.nodeCreateSock.listen(1)
        self.selectInput.append(self.nodeCreateSock)

        self.commandSendSock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM, socket.IPPROTO_UDP)
        self.commandSendSock.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
        self.commandSendSock.setsockopt(socket.SOL_SOCKET, socket.SO_BROADCAST, 1)

        
    def idGenerator(self, size = 20):
        chars = string.ascii_letters + string.digits
        return ''.join(random.choice(chars) for _ in range(size))
=======
        self.sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        self.sock.bind((self.host, self.port))
        self.sock.listen(1)
        self.keyvals = {}
        self.lock = threading.Lock()
        self.IDs = 1
>>>>>>> parent of d688208... non-central server can find existing central server

    def sendResponse(self, conn, response):
        responseJSON = json.dumps(response)
        conn.sendall(responseJSON.encode())
        return


    def handler(self, conn, addr):
        while True:
            try:
<<<<<<< HEAD
                readable, writable, exceptional = select.select(self.selectInput, self.selectOutput, self.selectInput)

                for s in readable:
                    if s is server:
                        conn, addr = s.accept()
                        conn.setblocking(0)
                        self.selectInput.append(conn)
                    else:
                        data = s.recv(1024).decode('utf-8')
                        if data:
                            request = json.loads(data)

                            
                            if ("command" in request and \
                                request["command"] == "contact central node"):

                                newNodeID = self.idGenerator()
                                responseJSON = {
                                    "id": newNodeID,
                                    "command": "contact acknowledge"
                                }
                                self.nonCentralNodes.append(newNodeID)
                                self.sendResponse(conn, responseJSON)

                            if ("return" not in request):
                                self.commandSendSock.sendto(request, ("<broadcast>", 37020))
                                
                        else:
                            if s in self.selectOutput:
                                self.selectOutput.remove(s)
                            self.selectInput.remove(s)
                            s.close()

                for s in exceptional:
                    self.selectInput.remove(s)
                    if s in self.selectOutput:
                        self.selectOutput.remove(s)
                    s.close()

=======
                data = conn.recv(1024).decode('utf-8')
                request = json.loads(data)
                print(request)

                if (len(request) != 3 or \
                    "id" not in request or \
                    "command" not in request or \
                    "payload" not in request):
                    print("Received request without valid format")
                    continue
                
                clientID = request["id"]
                command = request["command"]
                payload = request["payload"]

                # Build response object
                responseJSON = {
                    "id": clientID,
                    "return": []
                }

                # For each request object, exec command and add to response object
                for req in payload:
                    result = self.execRequest(clientID, command, req)
                    responseJSON["return"].append(result)
                
                self.sendResponse(conn, responseJSON)
>>>>>>> parent of d688208... non-central server can find existing central server

            except Exception as e:
                # print(type(e))
                # print(e)
                # conn.close()
                # print(str(addr[0])+':'+str(addr[1])+" Disconnected")
                # print("Failed at server handler")
                break

    def run(self):
        while True:
            (conn, addr) = self.sock.accept()
            cThread = threading.Thread(target=self.handler, args=(conn, addr))
            cThread.daemon = True
            cThread.start()
            
            print(str(addr[0])+':'+str(addr[1])+" Connected")


server = Server('localhost', 8001)

server.run()
