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

    def handler(self, conn, addr):
        while True:
            try:
                data = conn.recv(1024)
                request = json.loads(data)
                print(request)

                if "command" not in request:
                    print("Received request without valid format")
                    break
                
                command = request["command"].lower()

                if command == "create":
                    print("Create command received")
                    self.lock.acquire()
                    result = self.keyval.Create()
                    self.lock.release()
                    self.sendResponse(conn, result)

                elif command == "insert":
                    print("Insert command received")
                    if "payload" not in request:
                        print("Insert request without valid payload format")
                        break
                    payload = request["payload"]
                    key = payload["key"]
                    value = payload["value"]

                    self.lock.acquire()
                    result = self.keyval.Insert(key, value)
                    self.lock.release()
                    self.sendResponse(conn, result)

                elif command == "get":
                    print("Get command received")
                    payload = request["payload"]
                    key = payload["key"]

                    self.lock.acquire()
                    result = self.keyval.Get(key)
                    self.lock.release()
                    self.sendResponse(conn, result)

                elif command == "delete":
                    print("Delete command received")
                    payload = request["payload"]
                    key = payload["key"]

                    self.lock.acquire()
                    result = self.keyval.Delete(key)
                    self.lock.release()
                    self.sendResponse(conn, result)

                elif command == "find":
                    print("Find command received")
                    payload = request["payload"]
                    key = payload["key"]

                    self.lock.acquire()
                    result = self.keyval.Find(key)
                    self.lock.release()
                    self.sendResponse(conn, result)

                elif command == "update":
                    print("Update command received")
                    if "payload" not in request:
                        print("Insert request without valid payload format")
                        break
                    payload = request["payload"]
                    key = payload["key"]
                    value = payload["value"]

                    self.lock.acquire()
                    result = self.keyval.Update(key, value)
                    self.lock.release()
                    self.sendResponse(conn, result)

                elif command == "upsert":
                    print("Upsert command received")
                    if "payload" not in request:
                        print("Insert request without valid payload format")
                        break
                    payload = request["payload"]
                    key = payload["key"]
                    value = payload["value"]

                    self.lock.acquire()
                    result = self.keyval.UpSert(key, value)
                    self.lock.release()
                    self.sendResponse(conn, result)

                elif command == "clear":
                    print("Clear command received")
                    self.lock.acquire()
                    result = self.keyval.Clear()
                    self.lock.release()
                    self.sendResponse(conn, result)
                    
                elif command == "count":
                    print("Count command received")
                    self.lock.acquire()
                    result = self.keyval.Count()
                    self.lock.release()
                    self.sendResponse(conn, result)

            except Exception as e:
                # print(e)
                # conn.close()
                # print(str(addr[0])+':'+str(addr[1])+" Disconnected")
                # print("Failed at server handler")
                break

    def sendResponse(self, conn, result):
        response = { 
            "response": None 
        }
        response["response"] = result
        responseJSON = json.dumps(response)
        conn.sendall(responseJSON.encode())
        return

    def run(self):
        while True:
            (conn, addr) = self.sock.accept()
            cThread = threading.Thread(target=self.handler, args=(conn, addr))
            cThread.daemon = True
            cThread.start()
            
            print(str(addr[0])+':'+str(addr[1])+" Connected")


server = Server('localhost', 8001)

server.run()
