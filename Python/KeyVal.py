'''
Matthew Farias
Assignment1
COMP-4680
'''
class KeyVal:
    
    def Create(self):
        self.dic={}
    
    def Insert(self, k, v):    
        try:
            self.dic[k]=v
        except:
            print('KeyVal was not initialized')
    
    def Get(self, k):
        try:
            if k in self.dic:
                return self.dic[k]
            else:
                print('Key does not exist')
        except:
            print('KeyVal was not initialized')
    
    def Delete(self, k):
        try:
            if k in self.dic:
                self.dic.pop(k)
            else:
                print('Key does not exist')
        except:
            print('KeyVal was not initialized')
            
    def Find(self, k):
        try:
            return k in self.dic
        except:
            print('KeyVal was not initialized')

    def Update(self, k, v):
        try:
            if k in self.dic:
                self.dic[k] = v
            else:
                print('Key does not exist')
        except:
            print('KeyVal was not initialized')
    
    def UpSert(self, k, v):
        try:
            self.dic[k]=v
        except:
            print('KeyVal was not initialized')
    def Clear(self):
        try:
            self.dic.clear()
        except:
            print('KeyVal was not initialized')

    def Count(self):
        try:
            return len(self.dic)
        except:
            print('KeyVal was not initialized')

