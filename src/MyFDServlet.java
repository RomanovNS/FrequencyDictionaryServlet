import javax.servlet.annotation.MultipartConfig;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.Part;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.sql.SQLException;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import com.microsoft.sqlserver.jdbc.*;

import static java.nio.charset.StandardCharsets.UTF_16;
import static java.nio.charset.StandardCharsets.UTF_8;

@WebServlet(name = "/servlet")
@MultipartConfig
public class MyFDServlet extends javax.servlet.http.HttpServlet {
    public class SQLloaderJAR{
        int nothing;
        SQLloaderJAR(){
            try {
                System.out.println("Пытаюсь подключить драйвер MS SQL Server...");
                Class.forName("com.microsoft.sqlserver.jdbc.SQLServerDriver");
                System.out.println("Драйвер для подключения к MS SQL Server успешно загружен!");
            } catch (ClassNotFoundException e) {
                e.printStackTrace();
                System.out.println("Не получилось загрузить драйвер для подключения к MS SQL Server!");
            }
        }
    }
    SQLloaderJAR sqlLoaderJAR = new SQLloaderJAR();

    private boolean SaveFile(String Filename, byte[] bytes){
        FrequencyDictionary fd = new FrequencyDictionary(Filename, Filename, new String(bytes, StandardCharsets.UTF_8), 4);

        try {
            FreqDictDB fddb = new FreqDictDB("user002", "111222333");
            fddb.uploadFreqDict(fd);
        }catch (SQLException e){
            e.printStackTrace();
            System.out.println("Не получилось добавить частотный словарь в БД!");
            return false;
        }

        //File file;
        //try {
        //    Files.write(Paths.get(Filename), bytes);
        //    file = new File(Filename);
        //    System.out.println(file.getAbsolutePath());
        //    if (!file.exists()) return false;
        //} catch (IOException e) {
        //    e.printStackTrace();
        //    System.out.println("Не получилось сохранить файл!");
        //    return false;
        //}

        return true;
    }
    private String GetDictionaryJSON(String filename){
        FrequencyDictionary fd;
        try {
            FreqDictDB fddb = new FreqDictDB("user002", "111222333");
            fd = fddb.downloadFreqDict(filename);
            if (fd == null) return "{}";
        }catch (SQLException e){
            e.printStackTrace();
            System.out.println("Не получилось извлечь частотный словарь из БД!");
            return "{}";
        }
        String stringJSON = "{ \"fileName\": \"" + filename + "\",\n" +
                "\"wordsNumber\": " + fd.GetNumberOfAllWords() + ",\n" +
                "\"uniqueWordsNumber\": " + fd.GetNumberOfUniqueWords() + ",\n" +
                "\"dictionary\": [\n";
        Map<String, Integer> dict = fd.getDictionary();

        final String[] stringDict = {""};
        dict.forEach( (k, v) -> {
            stringDict[0] += "\t{ \"word\" : \"" + k + "\", \"count\" : " + v + ", \"percent\" : " +  (Math.round(v * 100000.0 / fd.GetNumberOfAllWords()) / 1000.0) + " },\n";
        });
        float a = 0;

        stringJSON += stringDict[0].substring(0, stringDict[0].length() - 2) + "]\n}";
        return stringJSON;
    }
    private String GetFilesListJSON(){
        List<String> filenames;
        try {
            FreqDictDB fddb = new FreqDictDB("user002", "111222333");
            filenames = fddb.getAvailableFileNames();
        }catch (SQLException e){
            e.printStackTrace();
            System.out.println("Не получилось извлечь список имён файлов из БД!");
            return "[]";
        }
        if (filenames.size() == 0) return "[]";

        final String[] filesJSON_temp = {""};

        try {
            FreqDictDB fddb = new FreqDictDB("user002", "111222333");
            filenames.forEach((filename) -> {
                filesJSON_temp[0] += "{ \"filename\": \"" + filename + "\", " +
                        "\"wordsNumber\": " + fddb.getWordsNumberInFile(filename) + ", " +
                        "\"uniqueWordsNumber\": " + fddb.getUniqueWordsNumberInFile(filename) + "},\n";
            });
        }catch (SQLException e){
            e.printStackTrace();
            System.out.println("Не получилось извлечь числа слов в файлах из БД!");
            return "[]";
        }

        return "[" + filesJSON_temp[0].substring(0, filesJSON_temp[0].length() - 2) + "]";
    }
    private String GetWordsListJSON(){
        List<String> words;
        try {
            FreqDictDB fddb = new FreqDictDB("user002", "111222333");
            words = fddb.getAvailableWords();
        }catch (SQLException e){
            e.printStackTrace();
            System.out.println("Не получилось извлечь список слов из БД!");
            return "[]";
        }
        final String[] wordsListJSON = {""};
        words.forEach((word)->{
            wordsListJSON[0] += " \"" + word + "\",";
        });

        return "[" + wordsListJSON[0].substring(0, wordsListJSON[0].length() - 1) + " ]";
    }
    private String GetFilesListWhereWordExistJSON(String word){
        Map<String, Integer> files;
        try {
            FreqDictDB fddb = new FreqDictDB("user002", "111222333");
            files = fddb.getFilenamesWhereWordExists(word);
            if (files == null) return "[]";
        }catch (SQLException e){
            e.printStackTrace();
            System.out.println("Не получилось извлечь список имён файлов из БД, где есть слово \"" + word + "\"!");
            return "[]";
        }
        if (files.size() == 0) return "[]";

        final String[] filesJSON_temp = {""};

        try {
            FreqDictDB fddb = new FreqDictDB("user002", "111222333");
            files.forEach((filename, wordCount) -> {
                filesJSON_temp[0] += "{ \"filename\" : \"" + filename + "\", " +
                        "\"thisWordCount\" : " + wordCount + ", " +
                        "\"wordsNumber\" : " + fddb.getWordsNumberInFile(filename) + ", " +
                        "\"thisWordPercent\" : " + (Math.round(wordCount * 100000.0 / fddb.getWordsNumberInFile(filename)) / 1000.0) + " },\n";
            });
        }catch (SQLException e){
            e.printStackTrace();
            System.out.println("Не получилось извлечь числа слов в файлах из БД!");
            return "[]";
        }
        if (filesJSON_temp[0].length() > 2)
            return "[" + filesJSON_temp[0].substring(0, filesJSON_temp[0].length() - 2) + "]";
        else return "[]";
    }

    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws javax.servlet.ServletException, IOException {
        request.setCharacterEncoding("UTF-8");
        response.setCharacterEncoding("UTF-8");
        System.out.println("Получил POST запрос со странички");

        final String[] command = new String[1];
        final String[] filename = new String[1];
        final String[] word = new String[1];
        final byte[][] fileBytes = new byte[1][1];

        Collection<Part> parts = request.getParts();
        parts.forEach((part)->{
            byte[] bytes = new byte[(int) part.getSize()];
            try {
                InputStream is = part.getInputStream();
                is.read(bytes,0, (int) part.getSize());
            } catch (IOException e) {
                e.printStackTrace();
            }
            switch (part.getName()){
                case "command" : {
                    command[0] = new String(bytes, UTF_8);
                    break;}
                case "filename" : {
                    filename[0] = new String(bytes, UTF_8);
                    break;}
                case "word" : {
                    word[0] = new String(bytes, UTF_8);
                    break;}
                case "file" : {
                    fileBytes[0] = bytes;
                    break;}
                default: break;
            }
        });

        String respString;
        switch (command[0]){
            case "namesOfAllFiles":{
                respString = GetFilesListJSON();
                break;}
            case "allWords":{
                respString = GetWordsListJSON();
                break;}
            case "namesOfFilesWhereWordExist":{
                respString = GetFilesListWhereWordExistJSON(word[0]);
                break;}
            case "dictionary":{
                respString = GetDictionaryJSON(filename[0]);
                break;}
            case "upload":{
                if (SaveFile(filename[0], fileBytes[0]))
                    respString = "Файл успешно добавлен в БД";
                else
                    respString = "Не получилось добавить файл в БД, возможно такой файл с таким названием уже загружен";
                break;}
            default: respString = "Error!"; break;
        }
        //System.out.println(respString);
        PrintWriter pw = response.getWriter();
        pw.write(respString);
    }
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws javax.servlet.ServletException, IOException {
        System.out.println("Получил GET запрос со странички");
        doPost(request, response);
    }
}
