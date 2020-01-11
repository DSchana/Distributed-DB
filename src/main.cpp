#include <DMap.h>
#include <iostream>

int main() {
    DMap<int, int> map;

    map[4] = 1;
    map[3] = 2;
    map[1] = 5;

    std::cout << (map.find(2) ? "yes" : "no") << std::endl;

    return 0;
}
