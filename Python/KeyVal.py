'''
Matthew Farias
Assignment1
COMP-4680
'''
class KeyVal:
    dic={}
    def create(self):
        self.dic={}
        return 'Successful'
    
    def insert(self, k, v):    
        if k not in self.dic:
            self.dic[k]=v
            return 'Successful'
        return 'Key already exists'
    
    def get(self, k):
        if k in self.dic:
            return self.dic[k]
        else:
            return 'Key does not exist'
    
    def delete(self, k):
        if k in self.dic:
            self.dic.pop(k)
            return 'Successful'
        else:
            return 'Key does not exist'
            
    def find(self, k):
        return k in self.dic.keys()

    def update(self, k, v):
        if k in self.dic:
            self.dic[k] = v
            return 'Successful'
        else:
            return 'Key does not exist'
    
    def upSert(self, k, v):
        self.dic[k]=v
        return 'Successful'

    def clear(self):
        self.dic.clear()
        return 'KeyVal Cleared'

    def count(self):
        return len(self.dic)

    def view(self):
        return str(self.dic)
