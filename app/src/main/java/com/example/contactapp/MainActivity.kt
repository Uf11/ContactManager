package com.example.contactapp

import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.viewModelScope
import androidx.navigation.*
import androidx.navigation.compose.*
import androidx.room.*
import com.example.contactapp.ui.theme.ContactAppTheme
import kotlinx.coroutines.*
import java.io.Serializable

@Entity(tableName = "contacts")
data class ContactEntity(
    @PrimaryKey(autoGenerate = false) val id: Long = 0,
    @ColumnInfo(name = "name") val name: String,
    @ColumnInfo(name = "phoneNumber") val phoneNumber: String,
    @ColumnInfo(name = "imageUri") val imageUri: String? = null
)
@Dao
interface ContactDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(contact: ContactEntity)
    @Query("SELECT * FROM contacts ORDER BY name ASC")
    fun getAllContacts(): LiveData<List<ContactEntity>>

    @Update
    suspend fun update(contact: ContactEntity)

    @Delete
    suspend fun delete(contact: ContactEntity)
}

@Database(entities = [ContactEntity::class], version = 1, exportSchema = true)
abstract class ContactDatabase : RoomDatabase() {
    abstract fun contactDao(): ContactDao
    companion object {
        @Volatile
        private var Instance: ContactDatabase? = null
        fun getDatabase(context: Context): ContactDatabase {
            // if the Instance is not null, return it, otherwise create a new database instance.
            return Instance ?: synchronized(this) {
                Room.databaseBuilder(context, ContactDatabase::class.java, "item_database")
                    .build()
                    .also { Instance = it }
            }
        }
    }
}

class ContactRepository(private val contactDao: ContactDao) {
    val allContacts: LiveData<List<ContactEntity>> = contactDao.getAllContacts()
    suspend fun insert(contact: ContactEntity) {
        contactDao.insert(contact)
    }
    suspend fun update(contact: ContactEntity) {
        Log.d("ContactRepository", "rUpdating contact with ID: ${contact.id}")
        contactDao.update(contact)
    }
    suspend fun delete(contact: ContactEntity) {
        contactDao.delete(contact)
    }
}

class ContactViewModel(application: Application) : AndroidViewModel(application), Serializable {

    private val repository: ContactRepository
    val allContacts: LiveData<List<ContactEntity>>

    init {
        val contactDao = ContactDatabase.getDatabase(application).contactDao()
        repository = ContactRepository(contactDao)
        allContacts = repository.allContacts
    }
    fun insert(contact: ContactEntity) = viewModelScope.launch(Dispatchers.IO) {
        repository.insert(contact)
    }
    fun update(contact: ContactEntity) = viewModelScope.launch(Dispatchers.IO) {
        Log.d("ContactViewModel", "vmUpdating contact with ID: ${contact.id}")
        repository.update(contact)
    }
    fun delete(contact: ContactEntity) = viewModelScope.launch(Dispatchers.IO) {
        repository.delete(contact)
    }
}

@Composable
fun RequestPermissionLauncher(
    permission: String,
    onPermissionResult: (Boolean) -> Unit
) {
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        onPermissionResult(isGranted)
    }
    DisposableEffect(Unit) {
        launcher.launch(permission)
        onDispose { /* Clean up if needed */ }
    }
}


class MainActivity : ComponentActivity() {
    private val permission : String = android.Manifest.permission.READ_CONTACTS

    val continueAction: (context : Context) -> Unit = { context ->
        val contactDatabase = Room.databaseBuilder(
            context.applicationContext,
            ContactDatabase::class.java, "contact-database"
        ).build()

        val intent = Intent(this, ContactMainActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        startActivity(intent)
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ContactAppTheme {
                val context = LocalContext.current
                if (ContextCompat.checkSelfPermission(
                        this,
                        permission
                    ) != PackageManager.PERMISSION_GRANTED
                ) {
                    RequestPermissionLauncher(permission = permission) { isGranted ->
                        if (!isGranted) {
                            finish()
                        } else {
                            continueAction(context)
                        }
                    }
                } else {
                    continueAction(context)
                }
            }
        }
    }
}