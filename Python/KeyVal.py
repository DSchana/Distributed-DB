'''
Matthew Farias
Assignment1
COMP-4680
'''
class KeyVal:
    dic={}
    def Create(self):
        self.dic={}
        return 'Successful'
    
    def Insert(self, k, v):    
        if k not in self.dic:
            self.dic[k]=v
            return 'Successful'
        return 'Key already exists'
    
    def Get(self, k):
        if k in self.dic:
            return self.dic[k]
        else:
            return 'Key does not exist'
    
    def Delete(self, k):
        if k in self.dic:
            self.dic.pop(k)
            return 'Successful'
        else:
            return 'Key does not exist'
            
    def Find(self, k):
        return k in self.dic

    def Update(self, k, v):
        if k in self.dic:
            self.dic[k] = v
            return 'Successful'
        else:
            return 'Key does not exist'
    
    def UpSert(self, k, v):
        self.dic[k]=v
        return 'Successful'
    def Clear(self):
        self.dic.clear()
        return 'KeyVal Cleared'

    def Count(self):
        return len(self.dic)
    def View(self):
        return str(self.dic)

