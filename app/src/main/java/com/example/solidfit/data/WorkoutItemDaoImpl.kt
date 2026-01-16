package com.example.solidfit.data

import com.example.solidfit.model.WorkoutItem
import com.hp.hpl.jena.rdf.model.Model
import com.hp.hpl.jena.rdf.model.ModelFactory
import com.hp.hpl.jena.rdf.model.ResourceFactory
import java.io.File
import java.util.Random
import kotlin.Int
import kotlin.String
import kotlin.collections.List
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import com.example.solidfit.data.Utilities.Companion.resourceToWorkoutItem

public class WorkoutItemDaoImpl(
  private val baseUri: String,
  private val baseDir: File,
  private var webId: String?,
) : WorkoutItemDao {
  private var model: Model

  private var saveFilePath: String

  public val modelLiveData: MutableStateFlow<List<WorkoutItem>> =
      kotlinx.coroutines.flow.MutableStateFlow(getAllWorkoutItems())

  init {
    val saveFilePath: String = if (webId != null) {
      webId!!.replace("https://", "").replace("http://",
        "").split("/").drop(1).joinToString(separator="_").replace("#", "")
    } else {
      generateRandomString(32)
    }
        this.saveFilePath = saveFilePath
    val model: Model = if (webId != null) {
      val file = File(baseUri, saveFilePath)
      if (file.exists()) {
        val inStream = file.inputStream()
        ModelFactory.createDefaultModel().read(inStream, null)
      } else {
        val model = ModelFactory.createDefaultModel()
        model.setNsPrefix("acp", Utilities.NS_ACP)
        model.setNsPrefix("acl", Utilities.NS_ACL)
        model.setNsPrefix("ldp", Utilities.NS_LDP)
        model.setNsPrefix("skos", Utilities.NS_SKOS)
        model.setNsPrefix("xsd", Utilities.NS_XSD)

    model.setNsPrefix("ti", Utilities.NS_WorkoutItem)
        model
      }
    } else {
      val model = ModelFactory.createDefaultModel()
      model.setNsPrefix("acp", Utilities.NS_ACP)
      model.setNsPrefix("acl", Utilities.NS_ACL)
      model.setNsPrefix("ldp", Utilities.NS_LDP)
      model.setNsPrefix("skos", Utilities.NS_SKOS)
      model.setNsPrefix("xsd", Utilities.NS_XSD)
        model.setNsPrefix("ti", Utilities.NS_WorkoutItem)
        model
    }
        this.model = model
  }

  override fun getAllWorkoutItemsAsFlow(): Flow<List<WorkoutItem>> = modelLiveData

  public override fun getAllWorkoutItems(): List<WorkoutItem> {
    val itemList = mutableListOf<WorkoutItem>()
    if (model == null) {
      return itemList
    }
    val res = model.listResourcesWithProperty(model.createProperty(
        Utilities.NS_WorkoutItem +
        "name"))
    while (res.hasNext()) {
      val nextResource = res.nextResource()
      itemList.add(resourceToWorkoutItem(nextResource))
    }
    return itemList
  }

  override suspend fun insert(item: WorkoutItem) {
    val id = java.util.UUID.randomUUID().toString()
    val mThingUri = model.createResource("$baseUri#$id")
    val mname = model.createProperty(Utilities.NS_WorkoutItem + "name")
    val nameLiteral = ResourceFactory.createTypedLiteral(item.name)
    mThingUri.addLiteral(mname, nameLiteral)
    val mdateCreated = model.createProperty(Utilities.NS_WorkoutItem + "dateCreated")
    val dateCreatedLiteral = ResourceFactory.createTypedLiteral(item.dateCreated)
    mThingUri.addLiteral(mdateCreated, dateCreatedLiteral)
    val mdateModified = model.createProperty(Utilities.NS_WorkoutItem + "dateModified")
    val dateModifiedLiteral = ResourceFactory.createTypedLiteral(item.dateModified)
    mThingUri.addLiteral(mdateModified, dateModifiedLiteral)
    val mdatePerformed = model.createProperty(Utilities.NS_WorkoutItem + "datePerformed")
    val datePerformedLiteral = ResourceFactory.createTypedLiteral(item.datePerformed)
    mThingUri.addLiteral(mdatePerformed, datePerformedLiteral)
    val mquantity = model.createProperty(Utilities.NS_WorkoutItem + "quantity")
    val quantityLiteral = ResourceFactory.createTypedLiteral(item.quantity)
    mThingUri.addLiteral(mquantity, quantityLiteral)
    val mduration = model.createProperty(Utilities.NS_WorkoutItem + "duration")
    val durationLiteral = ResourceFactory.createTypedLiteral(item.duration)
    mThingUri.addLiteral(mduration, durationLiteral)
    val mheartRate = model.createProperty(Utilities.NS_WorkoutItem + "heartRate")
    val heartRateLiteral = ResourceFactory.createTypedLiteral(item.heartRate)
    mThingUri.addLiteral(mheartRate, heartRateLiteral)
    val mworkoutType = model.createProperty(Utilities.NS_WorkoutItem + "workoutType")
    val workoutTypeLiteral = ResourceFactory.createTypedLiteral(item.workoutType)
    mThingUri.addLiteral(mworkoutType, workoutTypeLiteral)
    val mnotes = model.createProperty(Utilities.NS_WorkoutItem + "notes")
    val notesLiteral = ResourceFactory.createTypedLiteral(item.notes)
    mThingUri.addLiteral(mnotes, notesLiteral)
    val mmediaUri = model.createProperty(Utilities.NS_WorkoutItem + "mediaUri")
    val mediaUriLiteral = ResourceFactory.createTypedLiteral(item.mediaUri)
    mThingUri.addLiteral(mmediaUri, mediaUriLiteral)
    val file = File(baseDir, saveFilePath)
    val os = file.outputStream()
    model.write(os, null, null)
    modelLiveData.value = getAllWorkoutItems()
  }

  override suspend fun delete(uri: String) {
    val resource = ModelFactory.createDefaultModel().createResource("$baseUri#$uri")
    model.removeAll(resource, null ,null)
    val file = File(baseDir, saveFilePath)
    val os = file.outputStream()
    model.write(os, null, null)
    modelLiveData.value = getAllWorkoutItems()
  }

  override suspend fun update(item: WorkoutItem) {
    val resource = ResourceFactory.createResource("$baseUri#${item.id}")
    if (model.containsResource(resource)) {
      val resourceInModel = model.getResource(resource.uri)
      val mname = model.createProperty(Utilities.NS_WorkoutItem + "name")
      resourceInModel.removeAll(mname)
      val nameLiteral = ResourceFactory.createTypedLiteral(item.name)
      resourceInModel.addProperty(mname, nameLiteral)
      val mdateCreated = model.createProperty(Utilities.NS_WorkoutItem + "dateCreated")
      resourceInModel.removeAll(mdateCreated)
      val dateCreatedLiteral = ResourceFactory.createTypedLiteral(item.dateCreated)
      resourceInModel.addProperty(mdateCreated, dateCreatedLiteral)
      val mdateModified = model.createProperty(Utilities.NS_WorkoutItem + "dateModified")
      resourceInModel.removeAll(mdateModified)
      val dateModifiedLiteral = ResourceFactory.createTypedLiteral(item.dateModified)
      resourceInModel.addProperty(mdateModified, dateModifiedLiteral)
      val mdatePerformed = model.createProperty(Utilities.NS_WorkoutItem + "datePerformed")
      resourceInModel.removeAll(mdatePerformed)
      val datePerformedLiteral = ResourceFactory.createTypedLiteral(item.datePerformed)
      resourceInModel.addProperty(mdatePerformed, datePerformedLiteral)
      val mquantity = model.createProperty(Utilities.NS_WorkoutItem + "quantity")
      resourceInModel.removeAll(mquantity)
      val quantityLiteral = ResourceFactory.createTypedLiteral(item.quantity)
      resourceInModel.addProperty(mquantity, quantityLiteral)
      val mduration = model.createProperty(Utilities.NS_WorkoutItem + "duration")
      resourceInModel.removeAll(mduration)
      val durationLiteral = ResourceFactory.createTypedLiteral(item.duration)
      resourceInModel.addProperty(mduration, durationLiteral)
      val mheartRate = model.createProperty(Utilities.NS_WorkoutItem + "heartRate")
      resourceInModel.removeAll(mheartRate)
      val heartRateLiteral = ResourceFactory.createTypedLiteral(item.heartRate)
      resourceInModel.addProperty(mheartRate, heartRateLiteral)
      val mworkoutType = model.createProperty(Utilities.NS_WorkoutItem + "workoutType")
      resourceInModel.removeAll(mworkoutType)
      val workoutTypeLiteral = ResourceFactory.createTypedLiteral(item.workoutType)
      resourceInModel.addProperty(mworkoutType, workoutTypeLiteral)
      val mnotes = model.createProperty(Utilities.NS_WorkoutItem + "notes")
      resourceInModel.removeAll(mnotes)
      val notesLiteral = ResourceFactory.createTypedLiteral(item.notes)
      resourceInModel.addProperty(mnotes, notesLiteral)
      val mmediaUri = model.createProperty(Utilities.NS_WorkoutItem + "mediaUri")
      resourceInModel.removeAll(mmediaUri)
      val mediaUriLiteral = ResourceFactory.createTypedLiteral(item.mediaUri)
      resourceInModel.addProperty(mmediaUri, mediaUriLiteral)
      val file = File(baseDir, saveFilePath)
      val os = file.outputStream()
      model.write(os, null, null)
      modelLiveData.value = getAllWorkoutItems()
    } else {
      throw Error("item with ${item.id} not found.")
    }
  }

  public fun generateRandomString(length: Int): String {
    val characters = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"
    val randomString = StringBuilder()
    val random = Random()
    for (i in 0 until length) {
    val index = random.nextInt(characters.length)
    randomString.append(characters[index])
    }
    return randomString.toString()
  }

  override fun updateWebId(webId: String) {
    this.webId = webId
    this.saveFilePath = webId.replace("https://", "").replace("http://",
                "").split("/").drop(1).joinToString(separator="_").replace("#", "")
    val file = File(baseDir, saveFilePath)
    this.model = if (file.exists()) {
    val inStream = file.inputStream()
    ModelFactory.createDefaultModel().read(inStream, null)
    } else {
    val newModel = ModelFactory.createDefaultModel()
    newModel.setNsPrefix("acp", Utilities.NS_ACP)
    newModel.setNsPrefix("acl", Utilities.NS_ACL)
    newModel.setNsPrefix("ldp", Utilities.NS_LDP)
    newModel.setNsPrefix("skos", Utilities.NS_SKOS)
    newModel.setNsPrefix("xsd", Utilities.NS_XSD)
    newModel.setNsPrefix("ti", Utilities.NS_WorkoutItem)
    newModel
    }
    model.write(file.outputStream())
    modelLiveData.value = getAllWorkoutItems()
  }

  override fun resetModel() {
    val model = ModelFactory.createDefaultModel()
    model.setNsPrefix("acp", Utilities.NS_ACP)
    model.setNsPrefix("acl", Utilities.NS_ACL)
    model.setNsPrefix("ldp", Utilities.NS_LDP)
    model.setNsPrefix("skos", Utilities.NS_SKOS)
    model.setNsPrefix("xsd", Utilities.NS_XSD)
    model.setNsPrefix("ti", Utilities.NS_WorkoutItem)
    this.model = model
    modelLiveData.value = getAllWorkoutItems()
  }

  override suspend fun deleteAll() {
    val model = ModelFactory.createDefaultModel()
    model.setNsPrefix("acp", Utilities.NS_ACP)
    model.setNsPrefix("acl", Utilities.NS_ACL)
    model.setNsPrefix("ldp", Utilities.NS_LDP)
    model.setNsPrefix("skos", Utilities.NS_SKOS)
    model.setNsPrefix("xsd", Utilities.NS_XSD)
    model.setNsPrefix("ti", Utilities.NS_WorkoutItem)
    val file = File(baseDir, saveFilePath)
    val os = file.outputStream()
    model.write(os, null, null)
    this.model = model
    modelLiveData.value = getAllWorkoutItems()
  }

  override suspend fun overwriteModelWithList(items: List<WorkoutItem>) {
    resetModel()
    for (item in items) {
    val mThingUri = model.createResource("$baseUri#${item.id}")
    val mname = model.createProperty(Utilities.NS_WorkoutItem + "name")
    val nameLiteral = ResourceFactory.createTypedLiteral(item.name)
    mThingUri.addLiteral(mname, nameLiteral)
    val mdateCreated = model.createProperty(Utilities.NS_WorkoutItem + "dateCreated")
    val dateCreatedLiteral = ResourceFactory.createTypedLiteral(item.dateCreated)
    mThingUri.addLiteral(mdateCreated, dateCreatedLiteral)
    val mdateModified = model.createProperty(Utilities.NS_WorkoutItem + "dateModified")
    val dateModifiedLiteral = ResourceFactory.createTypedLiteral(item.dateModified)
    mThingUri.addLiteral(mdateModified, dateModifiedLiteral)
    val mdatePerformed = model.createProperty(Utilities.NS_WorkoutItem + "datePerformed")
    val datePerformedLiteral = ResourceFactory.createTypedLiteral(item.datePerformed)
    mThingUri.addLiteral(mdatePerformed, datePerformedLiteral)
    val mquantity = model.createProperty(Utilities.NS_WorkoutItem + "quantity")
    val quantityLiteral = ResourceFactory.createTypedLiteral(item.quantity)
    mThingUri.addLiteral(mquantity, quantityLiteral)
    val mduration = model.createProperty(Utilities.NS_WorkoutItem + "duration")
    val durationLiteral = ResourceFactory.createTypedLiteral(item.duration)
    mThingUri.addLiteral(mduration, durationLiteral)
    val mheartRate = model.createProperty(Utilities.NS_WorkoutItem + "heartRate")
    val heartRateLiteral = ResourceFactory.createTypedLiteral(item.heartRate)
    mThingUri.addLiteral(mheartRate, heartRateLiteral)
    val mworkoutType = model.createProperty(Utilities.NS_WorkoutItem + "workoutType")
    val workoutTypeLiteral = ResourceFactory.createTypedLiteral(item.workoutType)
    mThingUri.addLiteral(mworkoutType, workoutTypeLiteral)
    val mnotes = model.createProperty(Utilities.NS_WorkoutItem + "notes")
    val notesLiteral = ResourceFactory.createTypedLiteral(item.notes)
    mThingUri.addLiteral(mnotes, notesLiteral)
    val mmediaUri = model.createProperty(Utilities.NS_WorkoutItem + "mediaUri")
    val mediaUriLiteral = ResourceFactory.createTypedLiteral(item.mediaUri)
    mThingUri.addLiteral(mmediaUri, mediaUriLiteral)
    }
    val file = File(baseDir, saveFilePath)
    val os = file.outputStream()
    model.write(os, null, null)
    modelLiveData.value = getAllWorkoutItems()
  }

  override fun getWorkoutItemByIdAsFlow(id: String): Flow<WorkoutItem> {
    val toSearch = ResourceFactory.createResource("$baseUri#$id")
    if (model.containsResource(toSearch)) {
      return flowOf(resourceToWorkoutItem(model.getResource(toSearch.uri)))
    }
    return flowOf()
  }
}
