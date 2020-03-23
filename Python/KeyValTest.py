from KeyVal import *

mkv = KeyVal()
while True:
    print("Enter one of the following\n-1: Exit\n0: Print KeyVal\n1: Create KeyVal\n2: Insert\n3: Get\n4: Delete\n5: Find\n6: Update\n7: UpSert\n8: Clear\n9: Count")
    n=int(input())
    if n==-1:
        break
    elif n==0:
        print(mkv.dic)
    elif n==1:
        mkv.create()
    elif n==2:
        print("Enter Key")
        x=input()
        print("Enter Value")
        y=input()
        mkv.insert(x,y)
    elif n==3:
        print("Enter Key")
        x=input()
        print(mkv.get(x))
    elif n==4:
        x=input()
        print("Enter Key")
        print(mkv.delete(x))
    elif n==5:
        print("Enter Value")
        x=input()
        print(mkv.find(x))
    elif n==6:
        print("Enter Key")
        x=input()
        print("Enter Value")
        y=input()
        print(mkv.update(x,y))
    elif n==7:
        print("Enter Key")
        x=input()
        print("Enter Value")
        y=input()
        print(mkv.upSert(x,y))
    elif n==8:
        mkv.clear()
    elif n==9:
        print(mkv.count())
