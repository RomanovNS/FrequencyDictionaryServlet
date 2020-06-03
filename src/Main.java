import javafx.application.Application;
import javafx.stage.Stage;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.OpenOption;
import java.nio.file.Paths;
import java.sql.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

import static java.nio.charset.StandardCharsets.UTF_16;
import static java.nio.charset.StandardCharsets.UTF_8;

public class Main{

    static private boolean isDigit(String s) throws NumberFormatException {
        try {
            Integer.parseInt(s);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    public static class FrequencyDictionary{
        private Map<String, Integer> dictionary = new HashMap<String, Integer>();
        private int totalCounter = 0;
        private String wholeText;
        private String filePath;
        private String fileName;

        public static class DictionaryMaker implements Callable<Map<String, Integer>> {
            private String localText;

            private Map<String, Integer> makeDictionary(String text){
                Map<String, Integer> dictionaryLocal = new HashMap<String, Integer>();

                // сначала переведём всё в нижний регистр чтобы слова не различались из-за регистров
                String text_LowerCase = text.toLowerCase();

                // теперь заменим все знаки .,<>(){}[];:|\/_-+='"`~!@#№$%^&?* на пробелы  и сразу удалим повторяющиеся пробелы
                String text_onlyLettersAndNumebers = text_LowerCase.replaceAll("[.,<>(){};:|+-/|_='\"`~!@#№$%^&?—*]"," ")
                        .replace('[', ' ')
                        .replace(']', ' ')
                        .replace('\\', ' ')
                        .replaceAll("\\s+", " ");

                // а сейчас разделим по пробелам на отдельные слова
                String[] words = text_onlyLettersAndNumebers.split("\\s+");

                // а теперь запишем всё в dictionary, откидывая числа без включения буковок
                for(String word: words){
                    if (!isDigit(word) && !word.isEmpty()){
                        if (dictionaryLocal.containsKey(word)){
                            int currentCount = dictionaryLocal.get(word);
                            dictionaryLocal.replace(word, currentCount + 1);
                        }
                        else{
                            dictionaryLocal.put(word, 1);
                        }
                    }
                }
                return dictionaryLocal;
            }
            public DictionaryMaker(String textPart){
                localText = textPart;
            }
            @Override
            public Map<String, Integer> call() throws Exception {
                return makeDictionary(localText);
            }
        }

        void CreateDictionary(int threadsNum){
            ExecutorService executor = Executors.newCachedThreadPool();
            List<Future<Map<String, Integer>>> futureLocalDictionaries = new ArrayList<Future<Map<String, Integer>>>();

            // делим текст на равные (ну почти) кусочки так, чтобы не разрывать слова
            String[] textParts = new String[threadsNum];
            int textLength = wholeText.length();
            int substringStart = 0;
            int substringEnd = 0;
            int partMidLen = textLength / threadsNum;
            for(int i = 0; i < threadsNum; i++){
                // задаём начало подстроки
                substringStart = substringEnd;
                // задаём конец подстроки
                substringEnd = (substringStart + partMidLen < textLength)? substringStart + partMidLen : textLength - 1;
                while (wholeText.charAt(substringEnd) != ' ' && substringEnd < (textLength - 1))
                    substringEnd++;
                // вырезаем кусок текста в подстроку
                textParts[i] = wholeText.substring(substringStart, substringEnd);
            }

            // вызываем потоки и объединяем результаты их работы (локальные библиотеки)
            try{
                // вызываем потоки
                for(int i = 0; i < threadsNum; i++){
                    futureLocalDictionaries.add(executor.submit(new DictionaryMaker(textParts[i])));
                }
                // объединяем результаты
                for(int i = 0; i < threadsNum; i++){
                    Map<String, Integer> localDictionary = futureLocalDictionaries.get(i).get();
                    localDictionary.forEach( (k, v) -> dictionary.merge(k, v, (prevCount, count) -> prevCount + count));
                }
                // считаем общее количество слов
                dictionary.forEach( (k, v) -> totalCounter += v);
                // а теперь сортируем dictionary по ключу
                dictionary = dictionary.entrySet().stream()
                        .sorted(Map.Entry.comparingByKey())
                        .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue,
                                (oldValue, newValue) -> oldValue, LinkedHashMap::new));
            }
            catch (InterruptedException | ExecutionException exception){
                System.out.println("Что-то пошло не так при вызове потоков в FrequencyDictionary!");
                System.exit(666);
            }
            finally {
                executor.shutdown();
            }
        }

        public FrequencyDictionary(File file, int threads) throws IOException {
            wholeText = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
            filePath = file.getAbsolutePath();
            fileName = file.getName();
            CreateDictionary(threads);
        }
        public FrequencyDictionary(String _filePath, String _FileName, String internalText, int threads){
            wholeText = internalText;
            filePath = _filePath;
            fileName = _FileName;
            CreateDictionary(threads);
        }
        public FrequencyDictionary(String _filePath, String _fileName, Map<String, Integer> _dictionary){
            filePath = _filePath;
            fileName = _fileName;
            dictionary.putAll(_dictionary);
            dictionary = dictionary.entrySet().stream()
                    .sorted(Map.Entry.comparingByKey())
                    .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue,
                            (oldValue, newValue) -> oldValue, LinkedHashMap::new));
            dictionary.forEach( (k, v) -> totalCounter += v);
        }

        public int GetNumberOfAllWords(){
            return totalCounter;
        }
        public int GetNumberOfUniqueWords(){
            return dictionary.size();
        }
        public int GetWordCounter(String word){
            if (dictionary.containsKey(word.toLowerCase())){
                return dictionary.get(word);
            }
            else return 0;
        }
        public String getFilePath(){
            return filePath;
        }
        public String getFileName(){
            return fileName;
        }
        public Map<String, Integer>  getDictionary(){
            return dictionary;
        }
        public void PrintAllData(){
            System.out.println("File name: " + fileName);
            System.out.println("File path: " + filePath);
            System.out.println("Number of words: " + totalCounter);
            System.out.println("Number of unique words: " + dictionary.size());
            System.out.println("List:");
            System.out.println("Word - Count/Total - Percent");
            dictionary.forEach( (k, v)->System.out.println(k + " - " + v + "/" + totalCounter + " - " + (double)v * 100.0 / (double)totalCounter + "%") );
        }
    }

    public static class FreqDictDB{
        private Connection connection;

        public FreqDictDB(String login, String password) throws SQLException {
            connection = DriverManager.getConnection("jdbc:sqlserver://NIKITA-PC\\TEW_SQLEXPRESS;databaseName=FreqDictBD_v0.1;user=" + login + ";password=" + password + ";"); //login = user002;  password = 111222333
        }

        public FrequencyDictionary downloadFreqDict(int TextFileID){
            FrequencyDictionary fd = null;
            String fileName = getTextFileName(TextFileID);
            String filePath = getTextFilePath(TextFileID);
            Map<String, Integer> dict = getMapOfDictionary(TextFileID);
            // возвращаем если всё нашлось в БД
            if (fileName != null && filePath != null && dict != null) return new FrequencyDictionary(filePath, fileName, dict);
            else return null;
        }
        public FrequencyDictionary downloadFreqDict(String fileName_notPath){
            return downloadFreqDict(getTextFileID(fileName_notPath));
        }
        public void uploadFreqDict(FrequencyDictionary freqDict) throws SQLException {
            connection.setAutoCommit(false);
            try {
                PreparedStatement stmInsFile = connection.prepareStatement("INSERT INTO TextFile(filePath, fileName) VALUES( ? , ? )");
                PreparedStatement stmInsWord = connection.prepareStatement("INSERT INTO Word(word) VALUES( ? )");
                PreparedStatement stmInsTaW = connection.prepareStatement("INSERT INTO TextsAndWords(id_textFile, id_word, wordsCounter) VALUES(?, ?, ?)");

                // проверяем, есть ли уже такой файл, если нет, то добавляем
                int TextFileID = getTextFileID(freqDict.getFileName());
                if (TextFileID == 0){
                    stmInsFile.setString(1, freqDict.getFilePath());
                    stmInsFile.setString(2, freqDict.getFileName());
                    stmInsFile.execute();
                    // узнаём ID добавленного файла
                    TextFileID = getTextFileID(freqDict.getFileName());
                }
                else return; // если уже есть, то ничего не делаем

                // забиваем слова
                Map<String, Integer> dict = freqDict.getDictionary();
                int finalTextFileID = TextFileID;
                dict.forEach( (k, v) -> {
                    try{
                        // если слова нет то добавляем и получаем его ID
                        int wordID = getWordID(k);
                        if (wordID == 0){
                            stmInsWord.setString(1, k);
                            stmInsWord.execute();
                            wordID = getWordID(k);
                        }
                        // добавляем в TextsAndWords
                        stmInsTaW.setInt(1, finalTextFileID);
                        stmInsTaW.setInt(2, wordID);
                        stmInsTaW.setInt(3, v);
                        stmInsTaW.execute();
                    }catch (SQLException e){
                        e.printStackTrace();
                    }
                });

                // коммитим все добавления
                connection.commit();
            } catch (SQLException e) {
                e.printStackTrace();
                connection.rollback();
            } finally {
                connection.setAutoCommit(true);
            }
        }
        public int getTextFileID(String fileName_notPath){
            try {
                PreparedStatement stm = connection.prepareStatement("SELECT id FROM TextFile WHERE fileName = ?");
                stm.setString(1, fileName_notPath);
                ResultSet rs = stm.executeQuery();
                if(rs.next()) {
                    return rs.getInt("id");
                }
            } catch (SQLException e) {
                e.printStackTrace();
            }
            return 0;
        }
        public int getWordID(String word){
            try {
                PreparedStatement stm = connection.prepareStatement("SELECT id FROM Word WHERE word = ?");
                stm.setString(1, word);
                ResultSet rs = stm.executeQuery();
                if(rs.next()) {
                    return rs.getInt("id");
                }
            } catch (SQLException e) {
                e.printStackTrace();
            }
            return 0;
        }
        public String getTextFileName(int TextFileID){
            try {
                PreparedStatement stm = connection.prepareStatement("SELECT fileName FROM TextFile WHERE id = ?");
                stm.setInt(1, TextFileID);
                ResultSet rs = stm.executeQuery();
                if(rs.next()) {
                    return rs.getString("fileName");
                }
            } catch (SQLException e) {
                e.printStackTrace();
            }
            return null;
        }
        public String getTextFilePath(int TextFileID){
            try {
                PreparedStatement stm = connection.prepareStatement("SELECT filePath FROM TextFile WHERE id = ?");
                stm.setInt(1, TextFileID);
                ResultSet rs = stm.executeQuery();
                if(rs.next()) {
                    return rs.getString("filePath");
                }
            } catch (SQLException e) {
                e.printStackTrace();
            }
            return null;
        }
        public int getWordCountInFile(String fileName_notPath, String word){
            return getWordCountInFile(getTextFileID(fileName_notPath), word);
        }
        public int getWordCountInFile(int TextFileID, String word){
            int WordID = getWordID(word);
            if (TextFileID == 0 || WordID == 0) return 0;
            try {
                PreparedStatement stm = connection.prepareStatement("SELECT wordsCounter FROM TextsAndWords WHERE id_textFile = ? AND id_word = ?");
                stm.setInt(1, TextFileID);
                stm.setInt(2, WordID);
                ResultSet rs = stm.executeQuery();
                if(rs.next()) {
                    return rs.getInt("wordsCounter");
                }
            } catch (SQLException e) {
                e.printStackTrace();
            }
            return 0;
        }
        public int getWordsNumberInFile(int TextFileID){
            if (TextFileID == 0) return 0;
            int WordsNumber = 0;
            try {
                PreparedStatement stm = connection.prepareStatement("SELECT wordsCounter FROM TextsAndWords WHERE id_textFile = ?");
                stm.setInt(1, TextFileID);
                ResultSet rs = stm.executeQuery();
                while (rs.next()) {
                    WordsNumber += rs.getInt("wordsCounter");
                }
            } catch (SQLException e) {
                e.printStackTrace();
            }
            return WordsNumber;
        }
        public int getWordsNumberInFile(String fileName_notPath){
            return getWordsNumberInFile(getTextFileID(fileName_notPath));
        }
        public int getUniqueWordsNumberInFile(int TextFileID){
            if (TextFileID == 0) return 0;
            int UniqueWordsNumber = 0;
            try {
                PreparedStatement stm = connection.prepareStatement("SELECT wordsCounter FROM TextsAndWords WHERE id_textFile = ?");
                stm.setInt(1, TextFileID);
                ResultSet rs = stm.executeQuery();
                while (rs.next()) {
                    UniqueWordsNumber++;
                }
            } catch (SQLException e) {
                e.printStackTrace();
            }
            return UniqueWordsNumber;
        }
        public int getUniqueWordsNumberInFile(String fileName_notPath){
            return getUniqueWordsNumberInFile(getTextFileID(fileName_notPath));
        }
        public Map<String, Integer> getMapOfDictionary(String fileName_notPath){
            return getMapOfDictionary(getTextFileID(fileName_notPath));
        }
        public Map<String, Integer> getMapOfDictionary(int TextFileID){
            Map<String, Integer> map = new HashMap<String, Integer>();
            if (TextFileID == 0) return null;
            try {
                PreparedStatement stm = connection.prepareStatement("SELECT \n" +
                        "\tw.word,\n" +
                        "\ttaw.wordsCounter\n" +
                        "FROM TextsAndWords taw JOIN Word w ON taw.id_word = w.id\n" +
                        "WHERE taw.id_textFile = ?\n" +
                        "ORDER BY word");
                stm.setInt(1, TextFileID);
                ResultSet rs = stm.executeQuery();
                while (rs.next()) {
                    map.put(rs.getString("word"), rs.getInt("wordsCounter"));
                }
                map = map.entrySet().stream()
                        .sorted(Map.Entry.comparingByKey())
                        .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue,
                                (oldValue, newValue) -> oldValue, LinkedHashMap::new));
                return map;
            } catch (SQLException e) {
                e.printStackTrace();
            }
            return null;
        }
        public Map<String, Integer> getFilenamesWhereWordExists(String word){
            return getFilenamesWhereWordExists(getWordID(word));
        }
        public Map<String, Integer> getFilenamesWhereWordExists(int wordID){
            if (wordID == 0) return null;
            Map<String, Integer> map = new HashMap<String, Integer>();
            try {
                PreparedStatement stm = connection.prepareStatement("SELECT\n" +
                        "\ttf.fileName,\n" +
                        "\ttaw.wordsCounter\n" +
                        "FROM TextsAndWords taw JOIN TextFile tf ON taw.id_textFile = tf.id\n" +
                        "WHERE taw.id_word = ?\n" +
                        "ORDER BY tf.fileName");
                stm.setInt(1, wordID);
                ResultSet rs = stm.executeQuery();
                while (rs.next()) {
                    map.put(rs.getString("fileName"), rs.getInt("wordsCounter"));
                }
                map = map.entrySet().stream()
                        .sorted(Map.Entry.comparingByKey())
                        .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue,
                                (oldValue, newValue) -> oldValue, LinkedHashMap::new));
                return map;
            } catch (SQLException e) {
                e.printStackTrace();
            }
            return null;
        }
        public List<String> getAvailableFileNames(){
            List<String> list = new ArrayList<String>();
            try {
                Statement stm = connection.createStatement();
                ResultSet rs = stm.executeQuery("SELECT fileName FROM TextFile ORDER BY fileName");
                while (rs.next()) {
                    list.add(rs.getString("fileName"));
                }
                return list;
            } catch (SQLException e) {
                e.printStackTrace();
            }
            return null;
        }
        public List<String> getAvailableWords(){
            List<String> list = new ArrayList<String>();
            try {
                Statement stm = connection.createStatement();
                ResultSet rs = stm.executeQuery("SELECT word FROM Word ORDER BY word");
                while (rs.next()) {
                    list.add(rs.getString("word"));
                }
                return list;
            } catch (SQLException e) {
                e.printStackTrace();
            }
            return null;
        }
    }

    private static String GetFilesListWhereWordExist(String word){
        Map<String, Integer> files;
        try {
            FreqDictDB fddb = new FreqDictDB("user002", "111222333");
            files = fddb.getFilenamesWhereWordExists(word);
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
                        "\"wordsNumber\" : " + fddb.getWordsNumberInFile(filename) + "},\n";
            });
        }catch (SQLException e){
            e.printStackTrace();
            System.out.println("Не получилось извлечь числа слов в файлах из БД!");
            return "[]";
        }

        String filesJSON = "[" + filesJSON_temp[0].substring(0, filesJSON_temp[0].length() - 2) + "]";
        return filesJSON;
    }

    public static void main(String[] args) throws IOException, SQLException {
/*
        int threads = 4;
        long startTime = System.nanoTime();
        FrequencyDictionary fd = new FrequencyDictionary(new File("test book Пища богов.txt"), threads);
        long executionTime = System.nanoTime() - startTime;

        fd.PrintAllData();
        System.out.println();
        System.out.println("Execution time: " + executionTime / 1000000000.0 + " sec");
        System.out.println();

        System.out.println("Всего слов: " + fd.GetNumberOfAllWords());
        System.out.println("Уникальных слов: " + fd.GetNumberOfUniqueWords());
        System.out.println("Количество слов \"я\": " + fd.GetWordCounter("я") + " (" + fd.GetWordCounter("я") * 100.0 / fd.GetNumberOfAllWords() + "%)");
        System.out.println("Количество слов \"быть\": " + fd.GetWordCounter("быть") + " (" + fd.GetWordCounter("быть") * 100.0 / fd.GetNumberOfAllWords() + "%)");
        System.out.println("Количество слов \"ололо\": " + fd.GetWordCounter("ололо") + " (" + fd.GetWordCounter("ололо") * 100.0 / fd.GetNumberOfAllWords() + "%)");
*/
        //byte[] input = new byte[100];
        //System.in.read(input);
        //String str = new String(input);
        //System.out.println();
        //System.out.println(str);

        //try {
        //    Class.forName("com.microsoft.sqlserver.jdbc.SQLServerDriver");
        //} catch (ClassNotFoundException e) {
        //    e.printStackTrace();
        //}

        FreqDictDB fddb = new FreqDictDB("user002", "111222333");

        //FrequencyDictionary fd = new FrequencyDictionary(new File("zagotovka.txt"), 4);
        //fd.PrintAllData();
        //fddb.uploadFreqDict(fd);

        //Map<String, Integer> map = fddb.getFilenamesWhereWordExists("ололо");
        //map.forEach( (k, v)->System.out.println(k + " - " + v) );
        //System.out.println(fddb.getUniqueWordsNumberInFile(1)); //"test.txt"

        //FrequencyDictionary fd = fddb.downloadFreqDict("zagotovka.txt");
        //fd.PrintAllData();

        //List<String> list = fddb.getAvailableWords();
        //list.forEach( (v)->System.out.println(v) );

        //Files.write(Paths.get("newTest_WriteFile.txt"), "ololo".getBytes());
        //byte[] b = "olololololoolololl".getBytes();
        //System.out.println(new String(b));

        //FrequencyDictionary fd = new FrequencyDictionary(new File("zagotovka.txt"), 4);

        String json = GetFilesListWhereWordExist("упал");
        System.out.println(json);

        //File file = new File("test book Пища богов.txt");
        //System.out.println(Paths.get("test book Пища богов.txt"));

        //System.out.println(new String("ololoавававававlo!!!!!!!!!!!!!!".getBytes(UTF_8),UTF_8));
        //System.out.println("ololoавававававlo!!!!!!!!!!!!!!");
    }
}