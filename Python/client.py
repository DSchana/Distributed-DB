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
        return json.loads(result)
    
    def create(self):
        assert self.id == None
        message = { 
            "command": "create", 
            "payload": []
        }
        response = self.sendrecv(message)
        self.id = json.dumps(response)
        return json.dumps(response)

    def insert(self, key, value):
        assert self.id != None
        message = { 
            "command": "insert", 
            "payload": []
        } 

        payloadReqObj = {
            "key": None,
            "value": None
        }

        payloadReqObj["key"] = key
        payloadReqObj["value"] = value
        message["payload"].append(payloadReqObj)

        response = self.sendrecv(message)

        return json.dumps(response)

    def get(self, key):
        assert self.id != None
        message = {
            "command": "get",
            "payload": []
        }

        payloadReqObj = {
            "key": None
        }

        payloadReqObj["key"] = key
        message["payload"].append(payloadReqObj)

        response = self.sendrecv(message)
        returnObj = response["return"]
        return returnObj[0]["value"]

    def delete(self, key):
        assert self.id != None
        message = {
            "command": "get",
            "payload": []
        }

        payloadReqObj = {
            "key": None
        }

        payloadReqObj["key"] = key
        message["payload"].append(payloadReqObj)

        response = self.sendrecv(message)

        return json.dumps(response)
        

    def find(self, key):
        assert self.id != None
        message = {
            "command": "get",
            "payload": []
        }

        payloadReqObj = {
            "key": None
        }

        payloadReqObj["key"] = key
        message["payload"].append(payloadReqObj)

        response = self.sendrecv(message)
        returnObj = response["return"]
        return returnObj[0]["value"]

    def update(self, key, value):
        assert self.id != None
        message = { 
            "command": "insert", 
            "payload": []
        } 

        payloadReqObj = {
            "key": None,
            "value": None
        }

        payloadReqObj["key"] = key
        payloadReqObj["value"] = value
        message["payload"].append(payloadReqObj)

        response = self.sendrecv(message)
    
        return json.dumps(response)

    def upsert(self, key, value):
        assert self.id != None
        message = { 
            "command": "insert", 
            "payload": []
        } 

        payloadReqObj = {
            "key": None,
            "value": None
        }

        payloadReqObj["key"] = key
        payloadReqObj["value"] = value
        message["payload"].append(payloadReqObj)

        response = self.sendrecv(message)

        return json.dumps(response)

    def clear(self):
        assert self.id != None
        message = {
            "command": "clear",
            "payload": [{}]
        }

        response = self.sendrecv(message)

        return json.dumps(response)

    def count(self):
        assert self.id != None
        message = {
            "command": "count",
            "payload": [{}]
        }

        response = self.sendrecv(message)

        return json.dumps(response)


