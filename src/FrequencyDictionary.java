import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.sql.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

public class FrequencyDictionary {
    private Map<String, Integer> dictionary = new HashMap<String, Integer>();
    private int totalCounter = 0;
    private String wholeText;
    private String filePath;
    private String fileName;

    static private boolean isDigit(String s) throws NumberFormatException {
        try {
            Integer.parseInt(s);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

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