public class JSONTest{
    public static void main(String[] args){
        KeyValueStore<Integer, Integer> kvs = new KeyValueStore<Integer, Integer>();
        for(int i =0 ; i<100; i+=2){
            kvs.upSert(i,i+1);
        }
        System.out.println(kvs);
    }
}