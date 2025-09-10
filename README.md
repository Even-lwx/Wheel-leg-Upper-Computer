# DeepSeek-v3 APP for Android

This app showing how to make speech on DeepSeek-v3 with Java using the Speech SDK for Android.

## Need replace the parameters("****") in java file. eg: DeepSeek-v3 key and microsoft azure TTS key etc.. 

* MainActivity.java
   
<code>private static final String SpeechSubscriptionKey = "xxxx";
 pprivate static final String SpeechRegion = "xxxx";</code>


* ChatAPI.java
  
<code>
    HttpURLConnection connection = (HttpURLConnection) url.openConnection();
    connection.setRequestMethod("POST");
    connection.setRequestProperty("Content-Type", "application/json");
    connection.setRequestProperty("Authorization", "Bearer xxxx"); 
</code>    
## References

* [Speech SDK API reference for Java](https://aka.ms/csspeech/javaref)
