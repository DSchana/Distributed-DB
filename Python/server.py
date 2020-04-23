import socket
import threading
from KeyVal import *
import sys
import string
import json
import random
import select

class Server:
    def __init__(self, host, port):
        self.host = host
        self.port = port
        self.keyvals = {}
        self.lock = threading.Lock()
        self.IDs = []
        self.isCentralNode = False
        self.sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        self.sock.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1);
        self.sock.bind((self.host, self.port))
        self.sock.listen(1)
        self.selfID = None
        self.selectInput = [self.sock]
        self.selectOutput = []

        # Attempt to contact existing central node
        self.nodeCreateSock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        self.nodeCreateSock.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
        self.nodeCreateSock.settimeout(0.1)
        try:
            self.nodeCreateSock.connect((self.host, 8002))
            self.commandReceiveSock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM, socket.IPPROTO_UDP)
            self.commandReceiveSock.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
            self.commandReceiveSock.setsockopt(socket.SOL_SOCKET, socket.SO_BROADCAST, 1)
            self.selectInput.append(self.commandReceiveSock)
            print("Im a non-central node")
            message = { 
                "command": "contact central node"
            }
            messageJSON = json.dumps(message)
            self.nodeCreateSock.sendall(messageJSON.encode())

        except socket.timeout:
            print("Im the central node")
            self.keyStore = ()
            self.isCentralNode = True
            self.selfID = 1
            self.nonCentralNodes = []

            self.nodeCreateSock.close()
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

    def sendResponse(self, conn, response):
        # response = { 
        #     "response": None 
        # }
        # response["response"] = result
        responseJSON = json.dumps(response)
        conn.sendall(responseJSON.encode())
        return

    def execRequest(self, clientID, command, requestObj):
        command = command.lower()

        if command == "create":
            print("Create command received")
            self.lock.acquire()
            self.keyvals[clientID] = KeyVal()
            result = self.keyvals.get(clientID).create()
            self.lock.release()

            return { "status": 204 }

        elif command == "insert":
            print("Insert command received")
            if "key" not in requestObj or \
                "value" not in requestObj:
                print("Insert request with invalid payload format")
                return { "status": 400 }
                
            key = requestObj["key"]
            value = requestObj["value"]

            self.lock.acquire()
            result = self.keyvals[clientID].insert(key, value)
            self.lock.release()
            
            return { "status": 200 }

        elif command == "get":
            print("Get command received")
            if "key" not in requestObj:
                print("Get request with invalid payload format")
                return { "status": 400 }

            key = requestObj["key"]

            self.lock.acquire()
            result = self.keyvals[clientID].get(key)
            self.lock.release()

            if result == 'Key does not exist':
                return { "status": 404 }
            else:
                return { "status": 200, "value": result }

        elif command == "delete":
            print("Delete command received")
            if "key" not in requestObj:
                print("Delete request with invalid payload format")
                return { "status": 400 }

            key = requestObj["key"]

            self.lock.acquire()
            result = self.keyvals[clientID].delete(key)
            self.lock.release()

            if result == 'Key does not exist':
                return { "status": 404 }
            else: 
                return { "status": 204 }

        elif command == "find":
            print("Find command received")
            if "key" not in requestObj:
                print("Find request with invalid payload format")
                return { "status": 400 }

            key = requestObj["key"]

            self.lock.acquire()
            result = self.keyvals[clientID].find(key)
            self.lock.release()

            return { "status": 200, "value": result }

        elif command == "update":
            print("Update command received")
            if "key" not in requestObj or \
                "value" not in requestObj:
                print("Update request with invalid payload format")
                return { "status": 400 }

            key = requestObj["key"]
            value = requestObj["value"]

            self.lock.acquire()
            result = self.keyvals[clientID].update(key, value)
            self.lock.release()

            if result == 'Key does not exist':
                return { "status": 404 }
            else:
                return { "status": 204 }

        elif command == "upsert":
            print("Upsert command received")
            if "key" not in requestObj or \
                "value" not in requestObj:
                print("Find request with invalid payload format")
                return { "status": 400 }

            key = requestObj["key"]
            value = requestObj["value"]

            self.lock.acquire()
            result = self.keyvals[clientID].upSert(key, value)
            self.lock.release()

            return { "status": 204 }

        elif command == "clear":
            print("Clear command received")
            self.lock.acquire()
            result = self.keyvals[clientID].clear()
            self.lock.release()
            
            return { "status": 204 } 

        elif command == "count":
            print("Count command received")
            self.lock.acquire()
            result = self.keyvals[clientID].count()
            self.lock.release()

            return { "status": 200, "value": result }



    def handler(self, conn, addr):
        while True:
            try:
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

                            if (self.isCentralNode):
                                # incoming request is a new node in the system
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
                                # Handle for case self is a non-central node
                                if ("command" in request and \
                                    request["command"] == "contact acknowledge"):
                                    self.selfID = request["id"]
                                
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

                # data = conn.recv(1024).decode('utf-8')
                # request = json.loads(data)
                # print(request)

                # # Handle for if self is the central node
                # if (self.isCentralNode and \
                #     "command" in request and \
                #     request["command"] == "contact central node"):

                #     newNodeID = self.idGenerator()
                #     responseJSON = {
                #         "id": newNodeID,
                #         "command": "contact acknowledge"
                #     }
                #     self.nonCentralNodes.append(newNodeID)


                #     continue


                # # Handle for case self is a non-central node
                # if (len(request) != 3 or \
                #     "id" not in request or \
                #     "command" not in request or \
                #     "payload" not in request):
                #     print("Received request without valid format")
                #     continue
                
                # clientID = request["id"]
                # command = request["command"]
                # payload = request["payload"]

                # # Build response object
                # responseJSON = {
                #     "id": clientID,
                #     "return": []
                # }

                # # For each request object, exec command and add to response object
                # for req in payload:
                #     result = self.execRequest(clientID, command, req)
                #     responseJSON["return"].append(result)
                
                # self.sendResponse(conn, responseJSON)

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
