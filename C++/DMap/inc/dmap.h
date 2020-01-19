#ifndef DMAP_H
#define DMAP_H

#include <map>
#include <base64.h>

template <class K, class V>
class dmap {
    std::map<K, V> data;

public:
    dmap();
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
dmap<K, V>::dmap() = default;

template <class K, class V>
void dmap<K, V>::insert(K key, V value) {
    data[key] = value;
}

template <class K, class V>
void dmap<K, V>::erase(K key) {
    typename std::map<K, V>::iterator it;
    if ((it = data.find(key)) != data.end())
        data.erase(it);
}

template <class K, class V>
bool dmap<K, V>::find(K key) {
    return data.find(key) != data.end();
}

template <class K, class V>
void dmap<K, V>::update(K key, V value) {
    data[key] = value;
}

template <class K, class V>
void dmap<K, V>::clear() {
    data.clear();
}

template <class K, class V>
int dmap<K, V>::size() {
    return data.size();
}

template <class K, class V>
V& dmap<K, V>::operator[](K key) {
    return data[key];
}

#endif  // DMAP_H
