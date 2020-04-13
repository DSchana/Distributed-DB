#ifndef DMAP_H
#define DMAP_H

#include <arpa/inet.h>
#include <atomic>
#include <base64.h>
#include <chrono>
#include <cstring>
#include <future>
#include <fstream>
#include <ifaddrs.h>
#include <iostream>
#include <map>
#include <memory>
#include <mutex>
#include <netinet/in.h>
#include <rapidjson/document.h>
#include <rapidjson/stringbuffer.h>
#include <rapidjson/writer.h>
#include <stdexcept>
#include <sys/socket.h>
#include <sys/types.h>
#include <sys/wait.h>
#include <unistd.h>
#include <utility>
#include <vector>

typedef std::chrono::high_resolution_clock high_resolution_clock;

constexpr long outgoing_connection_timeout_ms = 5000;

bool isJSONValid(rapidjson::Document& doc);
std::string getStringFromJSON(rapidjson::Value const& doc);

template <class K, class V>
class dmap {
    std::map<std::string, std::string> data;
    std::future<int> nm_future;  // Async network manager

    // Networking
    bool running;

    // Network identity of this device
    std::string network_name;
    std::string network_ip, network_netmask;

    rapidjson::Document network_json;

    std::chrono::time_point<high_resolution_clock> vitals_pulse_timer;

    std::vector<std::future<int> > incoming_connections;
    std::vector<std::tuple<std::future<std::string>, int, std::chrono::time_point<high_resolution_clock>, std::shared_ptr<bool> > > outgoing_connections;  // Connection future, socket, timeout timer, needs to exists

    std::map<std::string, std::shared_ptr<bool> > vitals_connections;  // Map devices to their vitals check pulse's "needs to exist" boolean

    std::mutex incomming_connection_mutex;
    std::mutex outgoing_connection_mutex;
    std::mutex data_recv_mutex;

    int sock;
    struct sockaddr_in dst{};
    struct sockaddr_in serv{};
    socklen_t sock_size = sizeof(struct sockaddr_in);

    unsigned int port;

    void saveNetworkConfig();

    std::string createConnection(std::string const& msg, std::string const& dst_ip);
    int handleConnection();
    std::string parseMessage(std::string const& msg);

public:
    dmap(std::string const& config_path = "./.config/ddb.config");
    ~dmap();
    void insert(K key, V value);
    V& get(K key);
    bool erase(K key);
    bool find(K key);
    bool update(K key, V value);
    void upsert(K key, V value);
    void clear();
    int size();

    // Operators
    V& operator[](K key);

    // Networking
    int start();
    int stop();
};

template <class K, class V>
dmap<K, V>::dmap(std::string const& config_path) {
    using namespace rapidjson;

    running = true;

    /// Load from/Generate config files

    std::ifstream s_config(config_path, std::ifstream::in);
    if (!s_config) {
        throw std::runtime_error("Missing config file: " + config_path);
    }
    std::string ddb_config((std::istreambuf_iterator<char>(s_config)), std::istreambuf_iterator<char>());

    Document config_json;
    config_json.Parse(ddb_config.c_str());

    if (config_json.HasParseError()) {
        throw std::runtime_error("Error parsing ddb config");
    }
    if (!config_json.HasMember("port") || !config_json["port"].IsUint()) {
        throw std::runtime_error("Invlaid port format in config file");
    }

    port = config_json["port"].GetUint();

    memset(&serv, 0, sizeof(serv));
    serv.sin_family = AF_INET;
    serv.sin_addr.s_addr = htonl(INADDR_ANY);
    serv.sin_port = htons(port);

    sock = socket(AF_INET, SOCK_STREAM, 0);

    /// Network discovery

    std::ifstream in_n_config("./.config/network-discovery", std::ifstream::in);
    if (!s_config) {
        throw std::runtime_error("Missing network discovery file: " + config_path);
    }
    std::string network_config((std::istreambuf_iterator<char>(in_n_config)), std::istreambuf_iterator<char>());
    in_n_config.close();

    network_json.Parse(network_config.c_str());

    if (network_json.HasParseError()) {
        throw std::runtime_error("Error parsing network config");
    }

    if (network_json.HasMember("name")) {
        network_name = network_json["name"].GetString();
    }
    else {
        network_name = "Ko8gC7jXVR0fKjpu7VCnHW9rBL3nN0ejNLMJmTNXkIIEY3f3Jx3OejlpSGJtPYbSvx7eDUXN8zlrc/z9";  // TODO: Generate randomly
        network_json.AddMember("name", StringRef(network_name.c_str()), network_json.GetAllocator());

        saveNetworkConfig();
    }

    struct ifaddrs* ifas = nullptr;
    getifaddrs(&ifas);

    // Get ip address and subnet mask
    for (struct ifaddrs * ifa = ifas; ifa; ifa = ifa->ifa_next) {
        if (!ifa->ifa_addr) {
            continue;
        }

        if (ifa->ifa_addr->sa_family == AF_INET) {
            void* tmp_addr_ptr = &((struct sockaddr_in *)ifa->ifa_addr)->sin_addr;
            void* tmp_mask_ptr = &((struct sockaddr_in *)ifa->ifa_netmask)->sin_addr;

            char addr_buf[INET_ADDRSTRLEN];
            char mask_buf[INET_ADDRSTRLEN];

            inet_ntop(AF_INET, tmp_addr_ptr, addr_buf, INET_ADDRSTRLEN);
            inet_ntop(AF_INET, tmp_mask_ptr, mask_buf, INET_ADDRSTRLEN);

            //std::cout << ifa->ifa_name << ": " << addr_buf << "(" << mask_buf << ")"<< std::endl;

            if (strcmp(addr_buf, "127.0.0.1") != 0) {
                network_ip = addr_buf;
                network_netmask = mask_buf;
            }
        }
    }
    if (ifas) freeifaddrs(ifas);

    std::cout << "IP: " << network_ip << ", NETMASK: " << network_netmask << std::endl;
    std::string greeting_msg = R"({"command":"greetings","name":)" + network_name + ",\"ip\":" + network_ip + "}";

    // Ping old nodes in the network to update knowledge of network
    for (auto& device : network_json["devices"].GetArray()) {
        std::string old_ip = device["ip"].GetString();
        std::cout << "Sending greeting to " << device["name"].GetString() << " (" << device["ip"].GetString() << ")" << std::endl;

        outgoing_connection_mutex.lock();
        outgoing_connections.push_back(std::make_tuple(
                std::async(std::launch::async, &dmap<K, V>::createConnection, this, greeting_msg, old_ip),
                -1,
                high_resolution_clock::now(),
                std::make_shared<bool>(true)
        ));
        vitals_connections[device["name"].GetString()] = std::get<3>(*(outgoing_connections.end() - 1));
        outgoing_connection_mutex.unlock();

        // Wait for connection socket to be updated
        usleep(5000);
        outgoing_connection_mutex.lock();
        outgoing_connection_mutex.unlock();
    }

    nm_future = std::async(std::launch::async, &dmap<K, V>::start, this);
}

template <class K, class V>
dmap<K, V>::~dmap() {
    saveNetworkConfig();
}

/**
 * Store a new key value pair
 *
 * @param   Key to store the value at
 * @param   Value to store
 */
template <class K, class V>
void dmap<K, V>::insert(K key, V value) {
    data[key] = value;
}

/**
 * Get value stored at key
 *
 * @param   Key whose value to get
 *
 * @return  Reference to value stored at key
 */
template <class K, class V>
V& dmap<K, V>::get(K key) {
    return data[key];
}

/**
 * Erase key-value pair at key
 *
 * @param   Key where to erase
 *
 * @return  True is pair was erased
 *          False if key-value pair could not be found
 */
template <class K, class V>
bool dmap<K, V>::erase(K key) {
    typename std::map<K, V>::iterator it;
    if ((it = data.find(key)) != data.end()) {
        data.erase(it);
        return true;
    }
    return false;
}

/**
 * Check if key exists
 *
 * @param   Key to check
 *
 * @return  True if key was found
 *          False if key was not found
 */
template <class K, class V>
bool dmap<K, V>::find(K key) {
    return data.find(key) != data.end();
}

/**
 * Update value at key
 *
 * @param   Key whose value to update
 * @param   New value to be stored
 *
 * @return  True if key is found
 *          False if key is not found
 */
template <class K, class V>
bool dmap<K, V>::update(K key, V value) {
    if (find(key)) {
        data[key] = value;
        return true;
    }
    return false;
}

/**
 * Update the value of key if it exists, create a new
 * entry in the store if key does not exist
 *
 * @param   Key whose value to upSert
 * @param   Value to be stored
 */
template <class K, class V>
void dmap<K, V>::upsert(K key, V value) {
    data[key] = value;
}

/**
 * Remove all key-value pairs
 */
template <class K, class V>
void dmap<K, V>::clear() {
    data.clear();
}

/**
 * Get the number of key-value pairs stored
 *
 * @return  Number of key-value pairs
 */
template <class K, class V>
int dmap<K, V>::size() {
    return data.size();
}

template <class K, class V>
V& dmap<K, V>::operator[](K key) {
    return data[key];
}

/// Networking

/**
 * Launch network connection manager. This will launch and
 * manage connections on separate threads.
 */
template <class K, class V>
int dmap<K, V>::start() {
    signal(SIGPIPE, SIG_IGN);
    bind(sock, (struct sockaddr *) &serv, sizeof(struct sockaddr));
    listen(sock, 1);

    // Initialize vitals pulse
    std::string vital_msg = R"({"command":"finally we get the inheritance"})";
    vitals_pulse_timer = high_resolution_clock::now();

    while (running) {
        /// Incoming communications check

        if (incomming_connection_mutex.try_lock()) {
            incomming_connection_mutex.unlock();
            incoming_connections.push_back(std::async(std::launch::async, &dmap<K, V>::handleConnection, this));
        }
        else {
            std::vector<decltype(incoming_connections.begin())> expired_connections;
            for (int i = 0; i < incoming_connections.size(); i++) {
                if (incoming_connections[i].wait_for(std::chrono::microseconds(5)) == std::future_status::ready) {
                    incoming_connections[i].get();
                    expired_connections.push_back(incoming_connections.begin() + i);
                    std::cout << "Incomming message parsed and responded to" << std::endl;
                }
            }

            for (int i = 0; i < expired_connections.size(); i++) {
                incoming_connections.erase(expired_connections[i] - i);
            }
        }

        /// Outgoing communications check

        std::vector<decltype(outgoing_connections.begin())> expired_connections;
        for (auto conn_it = outgoing_connections.begin(); conn_it != outgoing_connections.end(); conn_it++) {
            std::chrono::milliseconds life_time = std::chrono::duration_cast<std::chrono::milliseconds>(high_resolution_clock::now() - std::get<2>(*conn_it));

            std::cout << std::get<1>(*conn_it) << ": " << life_time.count() << std::endl;
            if (!std::get<3>(*conn_it)) {
                expired_connections.push_back(conn_it);
                std::cout << "Connection doesn't need to exist" << std::endl;
            }
            else if (std::get<0>(*conn_it).wait_for(std::chrono::microseconds(5)) == std::future_status::ready) {
                std::string rsp = std::get<0>(*conn_it).get();
                parseMessage(rsp);
                expired_connections.push_back(conn_it);
                std::cout << "Outgoing message response parsed" << std::endl;
            }
            else if (life_time.count() >= outgoing_connection_timeout_ms) {
                expired_connections.push_back(conn_it);
                std::cout << "Timeout reached" << std::endl;
            }
        }

        outgoing_connection_mutex.lock();
        for (int i = 0; i < expired_connections.size(); i++) {
            auto e = expired_connections[i] - i;
            std::cout << "Closing " << std::get<1>(*e) << std::endl;
            *std::get<3>(*e) = !*std::get<3>(*e);
            close(std::get<1>(*e));
            outgoing_connections.erase(e);
        }
        outgoing_connection_mutex.unlock();

        /// Vitals check pulse

        // Ready the next pulse and clean up pulses that have timed out
        std::chrono::seconds time_since_last_vitals_check = std::chrono::duration_cast<std::chrono::seconds>(high_resolution_clock::now() - vitals_pulse_timer);
        std::vector<rapidjson::Value::ValueIterator> devices_to_delete;

        decltype(vitals_connections.begin()) vitals_connection_it;

        for (auto device_it = network_json["devices"].Begin(); device_it != network_json["devices"].End(); device_it++) {
            if ((vitals_connection_it = vitals_connections.find(device_it->GetObject()["name"].GetString())) == vitals_connections.end()) {  // Vital check not active on this device
                // Wait 3 min before performing next vitals check
                if (time_since_last_vitals_check.count() >= 180) {
                    std::cout << "Performing vital check on " << device_it->GetObject()["name"].GetString() << " (" << device_it->GetObject()["ip"].GetString() << ")" << std::endl;

                    outgoing_connection_mutex.lock();
                    outgoing_connections.push_back(std::make_tuple(
                            std::async(std::launch::async, &dmap<K, V>::createConnection, this, vital_msg, device_it->GetObject()["ip"].GetString()),
                            -1,
                            high_resolution_clock::now(),
                            std::make_shared<bool>(true)
                    ));
                    vitals_connections[device_it->GetObject()["name"].GetString()] = std::get<3>(*(outgoing_connections.end() - 1));
                    outgoing_connection_mutex.unlock();

                    // Wait for connection socket to be updated
                    usleep(5000);
                    outgoing_connection_mutex.lock();
                    outgoing_connection_mutex.unlock();
                }
            }
            else {  // Vital check already sent for this device
                if (!*(vitals_connection_it->second)) {  // Vital check timed out
                    // Remove device from network JSON
                    std::cout << "Removing device " << device_it->GetObject()["name"].GetString() << " (" << device_it->GetObject()["ip"].GetString() << ")" << std::endl;
                    devices_to_delete.push_back(device_it);

                    // Remove vitals connection
                    vitals_connections.erase(vitals_connection_it);
                }
            }
        }

        // Erase devices
        if (!devices_to_delete.empty()) {
            for (auto device_it : devices_to_delete) {
                network_json["devices"].Erase(device_it);
            }

            saveNetworkConfig();
        }
    }

    return 0;
}

/**
 * Kill and clean up the network connection manager thread
 */
template <class K, class V>
int dmap<K, V>::stop() {
    running = false;

    return 0;
}

/**
 * Save current network JSON
 */
template <class K, class V>
void dmap<K, V>::saveNetworkConfig() {
    std::ofstream out_n_config("./.config/network-discovery", std::ofstream::out | std::ofstream::trunc);
    out_n_config << getStringFromJSON(network_json) << std::flush;
    out_n_config.close();
}

/**
 * Instance of server infrastruction looking to connect to
 * a server instance and send information
 *
 * @return  Response of the request
 */
template <class K, class V>
std::string dmap<K, V>::createConnection(std::string const& msg, std::string const& dst_ip) {
    outgoing_connection_mutex.lock();
    std::cout << "Attempting to send message (" << msg << "); to ip (" << dst_ip << ")" << std::endl;

    struct sockaddr_in send_dst{};
    int send_sock = socket(AF_INET, SOCK_STREAM, 0);

    std::get<1>(*(outgoing_connections.end() - 1)) = send_sock;

    memset(&send_dst, 0, sizeof(send_dst));
    send_dst.sin_family = AF_INET;
    send_dst.sin_addr.s_addr = inet_addr(dst_ip.c_str());
    send_dst.sin_port = htons(port);
    outgoing_connection_mutex.unlock();

    connect(send_sock, (struct sockaddr*)&send_dst, sizeof(struct sockaddr_in));

    char rsp[1000];

    send(send_sock, msg.c_str(), strlen(msg.c_str()), 0);
    recv(send_sock, rsp, 1000, 0);

    close(send_sock);

    return rsp;
}

/**
 * Instance of server infrastructure waiting for a command
 * to come from a client. Checks if the message fits the
 * standard outlined by the communications protocol and
 * executes the request if it does.
 *
 * @return  0   if request successfully executied
 *          <0  otherwise
 */
template <class K, class V>
int dmap<K, V>::handleConnection() {
    using namespace rapidjson;

    // Since call to accept is blocking, the connection mutex will stay locked until
    // a connection is accepted which means a new connection thread will always be
    // ready to replace the old one, allowing for multiple async connections.
    incomming_connection_mutex.lock();
    int consock = accept(sock, (struct sockaddr*)&dst, &sock_size);
    incomming_connection_mutex.unlock();

    std::cout << "Handling connection" <<std::endl;

    if (consock) {
        char buf[1000];

        data_recv_mutex.lock();
        recv(consock, buf, 1000, 0);
        data_recv_mutex.unlock();

        std::string response = parseMessage(buf);

        // Return response JSON
        if (!response.empty()) {
            send(consock, response.c_str(), strlen(response.c_str()), 0);
        }

        close(consock);
    }
    else {
        return -1;
    }

    return 0;
}

/**
 * Parse content of a message and generate a response
 *
 * @param   Message to parse
 *
 * @return  Response message to return if needed
 */
template <class K, class V>
std::string dmap<K, V>::parseMessage(std::string const &msg) {
    using namespace rapidjson;

    std::cout << "Parseing message: " << msg << std::endl;

    Document response(kObjectType);
    bool respond = true;

    Document doc;
    doc.Parse(msg.c_str());

    Document::AllocatorType& allocator = response.GetAllocator();

    if (doc.HasParseError() || !isJSONValid(doc)) {
        response.AddMember("status", 400, allocator);
    }
    else {
        response.AddMember("id", Value(doc["id"].GetString(), allocator), allocator);

        Value return_array(kArrayType);
        std::string command = doc["command"].GetString();

        /// Network discovery

        if (command == "greetings") {
            Value new_node(kObjectType);

            new_node.AddMember("name", StringRef(doc["name"].GetString()), network_json.GetAllocator());
            new_node.AddMember("ip", StringRef(doc["ip"].GetString()), network_json.GetAllocator());

            network_json["devices"].GetArray().PushBack(new_node, network_json.GetAllocator());
            saveNetworkConfig();

            response.AddMember("command", "and to you", allocator);
            response.AddMember("name", StringRef(network_name.c_str()), allocator);
            response.AddMember("ip", StringRef(network_ip.c_str()), allocator);
        }
        else if (command == "and to you") {
            Value new_node(kObjectType);

            new_node.AddMember("name", StringRef(doc["name"].GetString()), network_json.GetAllocator());
            new_node.AddMember("ip", StringRef(doc["ip"].GetString()), network_json.GetAllocator());

            network_json["devices"].GetArray().PushBack(new_node, network_json.GetAllocator());
            saveNetworkConfig();

            respond = false;
        }
        else if (command == "farewell") {
            for (auto it = network_json["devices"].Begin(); it != network_json["devices"].End(); it++) {
                if (it->GetObject()["name"].GetString() == doc["name"].GetString()) {
                    network_json["devices"].Erase(it);
                }
            }

            saveNetworkConfig();

            respond = false;
        }
        else if (command == "finally we get the inheritance") {
            response.AddMember("command", "im not dead yet, piss off", allocator);
            response.AddMember("name", StringRef(network_name.c_str()), allocator);
        }
        else if (command == "im not dead yet, piss off") {
            auto to_delete = vitals_connections.find(doc["name"].GetString());

            if (to_delete != vitals_connections.end()) {
                *(to_delete->second) = !*(to_delete->second);
                vitals_connections.erase(to_delete);
            }

            respond = false;
        }

        /// Communication

        else {
            for (auto &payload : doc["payload"].GetArray()) {
                Value return_value(kObjectType);

                if (command == "insert") {
                    std::string key(payload["key"].GetString());
                    std::string value(payload["value"].GetString());

                    insert(key, value);

                    return_value.AddMember("status", 204, allocator);
                }
                else if (command == "get") {
                    std::string key(payload["key"].GetString());
                    std::string value = get(key);

                    return_value.AddMember("status", 200, allocator);
                    return_value.AddMember("value", Value(value.c_str(), allocator), allocator);
                }
                else if (command == "delete") {
                    std::string key(payload["key"].GetString());

                    if (erase(key)) {
                        return_value.AddMember("status", 204, allocator);
                    }
                    else {
                        return_value.AddMember("status", 404, allocator);
                    }
                }
                else if (command == "find") {
                    std::string key(payload["key"].GetString());

                    if (find(key)) {
                        return_value.AddMember("status", 204, allocator);
                    }
                    else {
                        return_value.AddMember("status", 404, allocator);
                    }
                }
                else if (command == "update") {
                    std::string key(payload["key"].GetString());
                    std::string value(payload["value"].GetString());

                    if (update(key, value)) {
                        return_value.AddMember("status", 204, allocator);
                    }
                    else {
                        return_value.AddMember("status", 404, allocator);
                    }
                }
                else if (command == "upsert") {
                    std::string key(payload["key"].GetString());
                    std::string value(payload["value"].GetString());

                    upsert(key, value);

                    return_value.AddMember("status", 204, allocator);
                }
                else if (command == "clear") {
                    clear();

                    return_value.AddMember("status", 204, allocator);
                }
                else if (command == "count") {
                    return_value.AddMember("status", 200, allocator);
                    return_value.AddMember("value", size(), allocator);
                }

                return_array.PushBack(return_value, allocator);
            }

            response.AddMember("return", return_array, allocator);
        }
    }

    if (respond) {
        return getStringFromJSON(response);
    }
    else {
        return "";
    }
}

/// Non-member functions

/**
 * Check if JSON is valid according to the communication protocol wiki
 *
 * @param   JSON object to be checked
 *
 * @return  True if doc is valid
 *          False if any errors are found in its format
 */
bool isJSONValid(rapidjson::Document& doc) {
    using namespace rapidjson;

    if (!(doc.HasMember("id") && doc["id"].IsString()) ||
        !(doc.HasMember("command") && doc["command"].IsString()) ||
        !doc.HasMember("payload")) {
        return false;
    }

    if (!doc["payload"].IsArray()) {
        return false;
    }

    std::string command = doc["command"].GetString();

    /// Network discovery
    if (command == "greetings" || command == "and to you") {
        if (!(doc.HasMember("name") && doc["name"].IsString()) ||
            !(doc.HasMember("ip") && doc["ip"].IsString())) {
            return false;
        }
    }
    else if (command == "farewell" || command == "im not dead yet, piss off") {
        if (!(doc.HasMember("name") && doc["name"].IsString())) {
            return false;
        }
    }
    else if (command == "finally we get the inheritance") {
        // Do nothing
    }

        /// Communication
    else {
        for (auto &payload : doc["payload"].GetArray()) {
            if (command == "insert" || command == "update" || command == "upsert") {  // Needs key and value
                if (!(payload.HasMember("key") && payload["key"].IsString()) ||
                    !(payload.HasMember("value") && payload["value"].IsString())) {
                    return false;
                }
            }
            else if (command == "get" || command == "delete" || command == "find") {  // Needs key only
                if (!(payload.HasMember("key") && payload["key"].IsString())) {
                    return false;
                }
            }
            else if (command == "clear" || command == "count") {  // Needs nothing
                continue;
            }
            else {
                return false;
            }
        }
    }

    return true;
}

/**
 * Convert RapidJSON object to string
 *
 * @param   RapidJSON Value to convert
 *
 * @return  String representation of the given Value
 */
std::string getStringFromJSON(rapidjson::Value const& doc) {
    rapidjson::StringBuffer buffer;

    buffer.Clear();

    rapidjson::Writer<rapidjson::StringBuffer> writer(buffer);
    doc.Accept(writer);

    return std::string(strdup(buffer.GetString()));
}

#endif  // DMAP_H
