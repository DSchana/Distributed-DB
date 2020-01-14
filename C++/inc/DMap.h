#ifndef DMAP_H
#define DMAP_H

#include <map>

template <class K, class V>
class DMap {
    std::map<K, V> data;

public:
    DMap();
    void insert(K key, V value);
    void erase(K key);
    bool find(K key);
    void update(K key, V value);
    void clear();
    int size();

    // Operators
    V& operator[](K key);
};

template <class K, class V>
DMap<K, V>::DMap() = default;

template <class K, class V>
void DMap<K, V>::insert(K key, V value) {
    data[key] = value;
}

template <class K, class V>
void DMap<K, V>::erase(K key) {
    typename std::map<K, V>::iterator it = data.begin();
    if ((it = data.find(key)) != data.end())
        data.erase(it);
}

template <class K, class V>
bool DMap<K, V>::find(K key) {
    return data.find(key) != data.end();
}

template <class K, class V>
void DMap<K, V>::update(K key, V value) {
    data[key] = value;
}

template <class K, class V>
void DMap<K, V>::clear() {
    data.clear();
}

template <class K, class V>
int DMap<K, V>::size() {
    return data.size();
}

template <class K, class V>
V& DMap<K, V>::operator[](K key) {
    return data[key];
}

#endif  // DMAP_H
