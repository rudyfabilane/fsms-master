# heroSMS
The best free SMS app in Philippines a modified version of QKSMS
## About ##
This is a modified version of QKSMS - https://github.com/moezbhatti/qksms to work as SMS gateway to Philippines. 
heroSMS purpose is to send SMS to Philippines via  SMS Gateway and to provide free SMS access to overseas filipino workers.

heroSMS version don't have MMS support and some of the features are removed to focus on free  sms service function.


## Set Up NOTES: ##


* In MessageListFragment.java check on the lines starting 1216 you will see the implementation of the ads then supply it with your own ad units..

* In Transaction.java class find these lines and supply your code for sending the message to your own SMS server.

  `class HttpSmsTask extends AsyncTask<Params, String, String> {


        @Override
        protected void onPreExecute() {
            super.onPreExecute();
        }

        @Override
        protected String doInBackground(Params... params) {
            
            /*
            
             Do something here to forward messages to your own server/SMS gateway
            */

            return null;
        }

        protected void onPostExecute(String message) {

            //Do something after sending
        }
    }`
    
    heroSMS is availble for download in Google play - https://play.google.com/store/apps/details?id=com.rojangames.freetextph
 