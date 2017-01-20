package server;

import org.junit.Test;
import java.io.File;

import static org.junit.Assert.assertEquals;

public class Testing {

    @Test
    public void testMakeDir(){
        ServerDispatcher disp = new ServerDispatcher(null, null, null, null, null);
        String path = "testing/this/dir/";
        disp.makeDir(path);
        File file = new File(path);
        assertEquals(file.isDirectory(), true);
    }
}
