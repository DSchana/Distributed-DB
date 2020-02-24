#node.py
import socket
import threading
from KeyVal import *
import sys
import json

class Server:
    def __init__(self, host, port):
        self.host = host
        self.port = port
        self.sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        self.sock.bind((self.host, self.port))
        self.sock.listen(1)
        self.keyval = KeyVal()
        self.lock = threading.Lock()
        self.IDs = 1

    def sendResponse(self, conn, response):
        # response = { 
        #     "response": None 
        # }
        # response["response"] = result
        responseJSON = json.dumps(response)
        conn.sendall(responseJSON.encode())
        return

    def execRequest(self, command, requestObj):
        command = command.lower()

        if command == "create":
            print("Create command received")
            self.lock.acquire()
            result = self.keyval.create()
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
            result = self.keyval.insert(key, value)
            self.lock.release()
            
            return { "status": 200 }

        elif command == "get":
            print("Get command received")
            if "key" not in requestObj:
                print("Get request with invalid payload format")
                return { "status": 400 }

            key = requestObj["key"]

            self.lock.acquire()
            result = self.keyval.get(key)
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
            result = self.keyval.delete(key)
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
            result = self.keyval.find(key)
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
            result = self.keyval.update(key, value)
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
            result = self.keyval.upSert(key, value)
            self.lock.release()

            return { "status": 204 }

        elif command == "clear":
            print("Clear command received")
            self.lock.acquire()
            result = self.keyval.clear()
            self.lock.release()
            
            return { "status": 204 } 

        elif command == "count":
            print("Count command received")
            self.lock.acquire()
            result = self.keyval.count()
            self.lock.release()

            return { "status": 200, "value": result }



    def handler(self, conn, addr):
        while True:
            try:
                data = conn.recv(1024)
                request = json.loads(data)
                print(request)

                # if (len(request) != 3 or \
                #     "command" not in request or \
                #     "payload" not in request):
                #     print("Received request without valid format")
                #     continue
                
                payload = request["payload"]
                command = request["command"]

                # Build response object
                responseJSON = {
                    "id": None,
                    "return": []
                }

                # For each request object, exec command and add to response object
                for req in payload:
                    result = self.execRequest(command, req)
                    responseJSON["return"].append(result)
                
                self.sendResponse(conn, responseJSON)

            except Exception as e:
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
