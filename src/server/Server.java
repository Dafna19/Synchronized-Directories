package server;
import java.io.FileWriter;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.concurrent.ConcurrentHashMap;

/**
 * сервер пишет в лог (текстовый файл) о освоих действиях
 */
//java Server port(0)
public class Server {
    private ServerSocket serverSocket;
    private FileWriter logFile;
    private SimpleDateFormat date = new SimpleDateFormat("HH:mm:ss z dd.MM.yyyy");
    private ArrayList<String> serverFiles = new ArrayList<>();//список имеющихся у сервера файлов
    private ArrayList<String> dynamicFolders = new ArrayList<>();
    private ConcurrentHashMap<String, ArrayList<String>> clients = new ConcurrentHashMap<>();


    public Server(int port) throws IOException {
        serverSocket = new ServerSocket(port);
        logFile = new FileWriter("serverLog.txt", true);//дозаписывает в конец
        logFile.write("\n\nServer started at ip: " + serverSocket.getInetAddress() +
                " port: " + port + " at " + date.format(new Date()));
        logFile.flush();
    }

    public static void main(String[] args) throws IOException {
        System.out.println("I'm working...");
        int port = Integer.parseInt(args[0]);
        new Server(port).run();
    }

    public void run() {
        while (true) {
            try {
                Socket socket = serverSocket.accept();    //подсоединение
                System.out.println("New client");
                ServerDispatcher client = new ServerDispatcher(socket, dynamicFolders, clients, logFile, serverFiles);
                client.start(); //запускаем
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}
