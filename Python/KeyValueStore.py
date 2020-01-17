def Create():
    """Init key-value store"""
    DMap = dict()
    return DMap


def Insert(DMap):
    """Insert a key / value pair DMap"""
    key = input("Enter key: ")
    value = int(input("Enter Value: "))
    DMap[key] = value
    return DMap


def Get(DMap):
    """Retrieve value of a key - can add a return here"""
    key = input("Enter a key: ")
    if key in DMap:
        result = DMap[key]
        print("DMap[" + key + "] = " + result)
    else:
        print(key + " not found in DMap")


def Delete(DMap):
    """Delete the value of a key"""
    key = input("Enter key to delete its value: ")
    if key in DMap:
        del DMap[key]
        print(key + " deleted from DMap")
    else:
        print(key + " not found in DMap")
    return DMap


def Find(DMap):
    """Check if key is present in DMap"""
    key = input("Enter key to find its value: ")
    if key in DMap:
        print(key + " present in DMap")
    else:
        print(key + " not found in DMap")


def Update(DMap):
    """Update the value of a key in DMap"""
    key = input("Enter key to update its value: ")
    if key in DMap:
        new_val = input("Enter a new value for key " + key + " : ")
        DMap[key] = new_val
    else:
        print(key + " not found in DMap")
    return DMap


def UpSert(DMap):
    """Update or insert a value to a key"""
    key = input("Enter key to update its value: ")
    newValue = input("enter new value for key " + key + " : ")
    if key in DMap:
        DMap[key] = newValue
    else:
        print(key + " not found in DMap, inserting it")
        DMap[key] = newValue
    return DMap


def Clear(DMap):
    """Remove everything in DMap"""
    DMap.clear()
    return DMap


def Count(DMap):
    """Count total number of key / value pairs in DMap"""
    count = len(DMap.keys())
    print("Total number of key-value pairs in the DMap is: ", count)


def main():
    # Menu to run functions?


if __name__ == '__main__':
    main()