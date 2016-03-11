import com.sun.jna.Structure
import sna.Library

// Create the structure returned by the call to getpwuid()
class PasswdLinux extends Structure {
  var pw_name: String = _   // username
  var pw_passwd: String = _ // user password
  var pw_uid: Int = _       // user ID
  var pw_gid: Int = _       // group ID
  var pw_gecos: String = _  // real name
  var pw_dir: String = _    // home directory
  var pw_shell: String = _  // shell program
}

// Create the structure returned by the call to getpwuid()
class PasswdDarwin extends Structure {
  var pw_name: String = _   // username
  var pw_passwd: String = _ // user password
  var pw_uid: Int = _       // user ID
  var pw_gid: Int = _       // group ID
  var pw_change: Int = _    // password change time
  var pw_class: String = _  // user access class
  var pw_gecos: String = _  // real name
  var pw_dir: String = _    // home directory
  var pw_shell: String = _  // shell program
  var pw_expire: Int = _    // account expiration
  var pw_fields: Int = _    // internal: field filled in
}



case class WhoAmIResult(username: String, uid: Int, gid: Int, home: String)

trait WhoAmI {

  // Get a handle to the C library
  val cLib = Library("c") 

  def whoAmI(): WhoAmIResult = {
    // Call geteuid() in C library to get the effective UID
    val uid = cLib.geteuid()[Int]

    // Call getpwuid to look up password entry for the UID
    val passwdent = cLib.getpwuid(uid)[PasswdLinux]

    WhoAmIResult(passwdent.pw_name, passwdent.pw_uid, passwdent.pw_gid, passwdent.pw_dir)
  }   

}
