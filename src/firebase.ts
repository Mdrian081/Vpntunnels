import database from '@react-native-firebase/database';

// google-services.json থেকে firebase_url নেওয়া হয়েছে
export const db = database('https://vpnproject-aa346-default-rtdb.firebaseio.com');

export const serversRef = () => db.ref('servers');
