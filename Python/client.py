import socket
import json

class Client:
    def __init__(self, host, port):
        self.host = host
        self.port = port
        self.id = None

    def sendrecv(self, message):
        sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        sock.connect((self.host, self.port))
        messageJSON = json.dumps(message)
        sock.sendall(messageJSON.encode())
        result = sock.recv(1024).decode()
        return result
    
    def create(self):
        assert self.id == None
        message = { 
            "command": "create", 
            "payload": {}
        }
        self.id = self.sendrecv(message)
        return self.id

    def insert(self, key, value):
        assert self.id != None
        message = { 
            "command": "insert", 
            "payload": { 
                "key": None,
                "value": None
            } 
        } 

        message["payload"]["key"] = key
        message["payload"]["value"] = value

        return self.sendrecv(message)

    def get(self, key):
        assert self.id != None
        message = {
            "command": "get",
            "payload": {
                "key": None,
                "value": None
            }
        }

        message["payload"]["key"] = key
        return self.sendrecv(message)

    def delete(self, key):
        assert self.id != None
        message = {
            "command": "delete",
            "payload": {
                "key": None,
                "value": None
            }
        }

        message["payload"]["key"] = key
        return self.sendrecv(message)
        

    def find(self, key):
        assert self.id != None
        message = {
            "command": "find",
            "payload": {
                "key": None,
                "value": None
            }
        }

        message["payload"]["key"] = key
        return self.sendrecv(message)

    def update(self, key, value):
        assert self.id != None
        message = {
            "command": "update",
            "payload": {
                "key": None,
                "value": None
            }
        }

        message["payload"]["key"] = key
        return self.sendrecv(message)

    def upsert(self, key, value):
        assert self.id != None
        message = {
            "command": "upsert",
            "payload": {
                "key": None,
                "value": None
            }
        }

        message["payload"]["key"] = key
        message["payload"]["value"] = value

        return self.sendrecv(message)

    def clear(self):
        assert self.id != None
        message = {
            "command": "clear",
            "payload": {
                "key": None,
                "value": None
            }
        }

        return self.sendrecv(message)

    def count(self):
        assert self.id != None
        message = {
            "command": "count",
            "payload": {
                "key": None,
                "value": None
            }
        }

        return self.sendrecv(message)


