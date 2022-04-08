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

    /**
     * update store state to beginStore
     * @param fileName
     * @param size
     * @return
     */
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

    /**
     * update state to end
     * @param fileName
     * @param fileSizes
     * @param dstoresSet
     * @param success
     */

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

    /**
     * update state to begin remove
     * @param fileName
     * @return
     */

   public Set<Dstores> beginRemove(String fileName){
       Set<Dstores> dstoresSet = new HashSet<>();
       try {
           dstoresSet = index.get(fileName);

       }catch (Exception e){
           e.printStackTrace();
       }
       if (dstoresSet!= null){
           try {
               index.put(fileName,null);

           }finally {
               for (Dstores ds : dstoresSet){
                   ds.remove(fileName);
               }
           }
       }
       return dstoresSet;
   }

    /**
     * update state to end remove
     * @param fileName
     */

   public void endRemove(String fileName){
       try {
           this.index.remove(fileName);
           this.fileSize.remove(fileName);
       }catch (Exception e){
           e.printStackTrace();
       }
   }

    /**
     * it is used to tell Controller await to store
     * @param filename
     * @return
     */

    public boolean awaitStore(String filename) {
        try {
            return this.storeAcks.get(filename).await(this.controller.getTimeout(), TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * it is used to get dstores which store files
     * @param filename
     * @return
     */
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

    /**
     * it is used to tell controller await to remove fail
     * @param filename
     * @return
     */
    public boolean awaitRemove(String filename) {
        try {
            return this.removeAcks.get(filename).await(this.controller.getTimeout(), TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * it is used to get all file which is stored
     * @return
     */
    public Set<String> fileList() {
       return this.index.keySet();
   }
}
