import socket
import threading
from KeyVal import *
import sys
import string
import json
import random
import select

class Node:
    def __init__(self, host, port):
        self.host = host
        self.port = port
        self.keyvals = {}
        self.lock = threading.Lock()
        self.IDs = []
        self.isCentralNode = False
        self.sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)

        self.selfID = None
        self.selectInput = [self.sock]
        self.selectOutput = []

        self.nodeCreateSock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        self.nodeCreateSock.settimeout(0.1)
        self.nodeCreateSock.connect((self.host, 8002))

        self.commandReceiveSock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM, socket.IPPROTO_UDP)
        self.commandReceiveSock.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
        self.commandReceiveSock.setsockopt(socket.SOL_SOCKET, socket.SO_BROADCAST, 1)
        self.commandReceiveSock.bind((self.host, 37020))
        self.selectInput.append(self.commandReceiveSock)

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

    def sendResponse(self, conn, response):
        # response = { 
        #     "response": None 
        # }
        # response["response"] = result
        responseJSON = json.dumps(response)
        conn.sendall(responseJSON.encode())
        return

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


server = Node('localhost', 8001)

server.run()
