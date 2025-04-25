import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Updates;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.*;
import org.bson.Document;
import org.bson.types.ObjectId;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

@Path("/api/messages")
@Produces(MediaType.APPLICATION_JSON)
@ApplicationScoped
public class MessageService {

    @Inject
    MongoClient mongoClient;

    private MongoCollection<Document> getCollection() {
        return mongoClient.getDatabase("db-CERI").getCollection("CERISoNet");
    }

    @GET
    public List<Document> getAllMessages(@QueryParam("limit") @DefaultValue("20") int limit) {
        return StreamSupport.stream(
                        getCollection().find()
                                .sort(new Document("date", -1).append("hour", -1))
                                .limit(limit)
                                .spliterator(), false)
                .collect(Collectors.toList());
    }

    @GET
    @Path("/{id}")
    public Document getMessageById(@PathParam("id") String id) {
        try {
            Document message = getCollection().find(Filters.eq("_id", new ObjectId(id))).first();
            if (message == null) {
                throw new NotFoundException("Message non trouvé");
            }
            return message;
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("ID de message invalide");
        }
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    public Response createMessage(Document messageData) {
        if (!messageData.containsKey("body") || messageData.getString("body").trim().isEmpty()) {
            throw new BadRequestException("Le contenu du message ne peut pas être vide");
        }

        if (!messageData.containsKey("createdBy")) {
            throw new BadRequestException("L'ID de l'auteur est requis");
        }

        // Date et heure actuelles
        LocalDate today = LocalDate.now();
        LocalTime now = LocalTime.now();
        DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss");

        // Préparation du document
        Document document = new Document();
        document.append("body", messageData.getString("body"))
                .append("createdBy", messageData.getInteger("createdBy"))
                .append("date", today.format(dateFormatter))
                .append("hour", now.format(timeFormatter))
                .append("likes", 0)
                .append("likedBy", new ArrayList<>())
                .append("comments", new ArrayList<>());

        // Ajout des champs optionnels
        if (messageData.containsKey("hashtags")) {
            document.append("hashtags", messageData.get("hashtags"));
        }

        if (messageData.containsKey("images")) {
            document.append("images", messageData.get("images"));
        }

        // Insertion dans MongoDB
        getCollection().insertOne(document);
        String messageId = document.getObjectId("_id").toString();

        return Response.status(Response.Status.CREATED)
                .entity(new Document("id", messageId).append("message", "Message créé avec succès"))
                .build();
    }

    @PUT
    @Path("/{id}")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response updateMessage(@PathParam("id") String id, Document updatedMessage) {
        try {
            Document existingMessage = getCollection().find(Filters.eq("_id", new ObjectId(id))).first();
            if (existingMessage == null) {
                throw new NotFoundException("Message non trouvé");
            }

            if (!updatedMessage.containsKey("body") || updatedMessage.getString("body").trim().isEmpty()) {
                throw new BadRequestException("Le contenu du message ne peut pas être vide");
            }

            getCollection().updateOne(
                    Filters.eq("_id", new ObjectId(id)),
                    Updates.set("body", updatedMessage.getString("body"))
            );

            return Response.ok(new Document("message", "Message mis à jour")).build();
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("ID de message invalide");
        }
    }

    @DELETE
    @Path("/{id}")
    public Response deleteMessage(@PathParam("id") String id) {
        try {
            Document existingMessage = getCollection().find(Filters.eq("_id", new ObjectId(id))).first();
            if (existingMessage == null) {
                throw new NotFoundException("Message non trouvé");
            }

            getCollection().deleteOne(Filters.eq("_id", new ObjectId(id)));

            return Response.ok(new Document("message", "Message supprimé")).build();
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("ID de message invalide");
        }
    }

    @POST
    @Path("/{id}/like")
    public Response likeMessage(@PathParam("id") String id, @QueryParam("userId") int userId) {
        try {
            Document existingMessage = getCollection().find(Filters.eq("_id", new ObjectId(id))).first();
            if (existingMessage == null) {
                throw new NotFoundException("Message non trouvé");
            }

            // Vérifier si l'utilisateur a déjà aimé ce message
            List<Integer> likedBy = existingMessage.getList("likedBy", Integer.class);
            if (likedBy != null && likedBy.contains(userId)) {
                throw new BadRequestException("L'utilisateur a déjà aimé ce message");
            }

            // Ajouter le like
            getCollection().updateOne(
                    Filters.eq("_id", new ObjectId(id)),
                    Updates.combine(
                            Updates.inc("likes", 1),
                            Updates.push("likedBy", userId)
                    )
            );

            return Response.ok(new Document("message", "Like ajouté")).build();
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("ID de message invalide");
        }
    }

    @POST
    @Path("/{id}/comment")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response addComment(@PathParam("id") String id, Document commentData) {
        try {
            Document existingMessage = getCollection().find(Filters.eq("_id", new ObjectId(id))).first();
            if (existingMessage == null) {
                throw new NotFoundException("Message non trouvé");
            }

            if (!commentData.containsKey("text") || commentData.getString("text").trim().isEmpty()) {
                throw new BadRequestException("Le contenu du commentaire ne peut pas être vide");
            }

            if (!commentData.containsKey("commentedBy")) {
                throw new BadRequestException("L'ID de l'auteur du commentaire est requis");
            }

            // Date et heure actuelles
            LocalDate today = LocalDate.now();
            LocalTime now = LocalTime.now();
            DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
            DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss");

            // Création du document commentaire
            Document comment = new Document();
            comment.append("commentedBy", commentData.getInteger("commentedBy"))
                    .append("text", commentData.getString("text"))
                    .append("date", today.format(dateFormatter))
                    .append("hour", now.format(timeFormatter));

            // Ajout du commentaire au message
            getCollection().updateOne(
                    Filters.eq("_id", new ObjectId(id)),
                    Updates.push("comments", comment)
            );

            return Response.ok(new Document("message", "Commentaire ajouté")).build();
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("ID de message invalide");
        }
    }

    @GET
    @Path("/search")
    public List<Document> searchMessages(
            @QueryParam("term") String searchTerm,
            @QueryParam("limit") @DefaultValue("10") int limit) {

        if (searchTerm == null || searchTerm.trim().isEmpty()) {
            throw new BadRequestException("Le terme de recherche est requis");
        }

        return StreamSupport.stream(
                        getCollection().find(Filters.regex("body", ".*" + searchTerm + ".*", "i"))
                                .sort(new Document("date", -1).append("hour", -1))
                                .limit(limit)
                                .spliterator(), false)
                .collect(Collectors.toList());
    }

    @GET
    @Path("/hashtag/{hashtag}")
    public List<Document> getMessagesByHashtag(
            @PathParam("hashtag") String hashtag,
            @QueryParam("limit") @DefaultValue("10") int limit) {

        return StreamSupport.stream(
                        getCollection().find(Filters.eq("hashtags", hashtag))
                                .sort(new Document("date", -1).append("hour", -1))
                                .limit(limit)
                                .spliterator(), false)
                .collect(Collectors.toList());
    }
}