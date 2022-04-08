import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * @author gongyihui
 */
public class Index {
    private final Controller controller;
    private final HashMap<String, Set<Dstores>> index;
    private final HashMap<String, Integer> fileSize;
    private final HashMap<String, CountDownLatch> storeAcks;
    private final HashMap<String, CountDownLatch> removeAcks;

   public Index(Controller controller){
       this.controller = controller;
       this.index = new HashMap<>();
       this.fileSize = new HashMap<>();
       this.storeAcks = new HashMap<>();
       this.removeAcks = new HashMap<>();
   }

   public boolean beginStore(String fileName, int size){
       if (index.containsKey(fileName)){
           return false;
       }
       try {
           index.put(fileName,null);
           fileSize.put(fileName,size);

       }catch (Exception e){
           e.printStackTrace();
       }
       this.storeAcks.put(fileName,new CountDownLatch(controller.getR()));
       return true;
   }

   public void endStore(String fileName,int fileSizes, Set<Dstores> dstoresSet,boolean success){
       if (success){
           for (Dstores dstores: dstoresSet){
               dstores.store(fileName,fileSizes);
           }
       }else {
           index.remove(fileName);
           fileSize.remove(fileName);
       }
   }

    public boolean awaitStore(String filename) {
        try {
            return this.storeAcks.get(filename).await(this.controller.getTimeout(), TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            return false;
        }
    }
    public Set<Dstores> getFileDstores(String filename) {
        Set<Dstores> ds = null;
        try {
            if (this.index.get(filename) != null) {
                ds = this.index.get(filename);
            }

    }catch (Exception e){
            e.printStackTrace();
        }
        return ds;
    }
}
