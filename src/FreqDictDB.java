import java.sql.*;
import java.util.*;
import java.util.stream.Collectors;

public class FreqDictDB{
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
