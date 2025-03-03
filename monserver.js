import express from "express";
import cors from "cors";
import fs from "fs";
import https from "https";
import http from "http";
import path from "path";
import { fileURLToPath } from 'url';
import { dirname } from 'path';

// Obtention du chemin absolu du fichier
const __filename = fileURLToPath(import.meta.url);
const __dirname = dirname(__filename);

const PORT_HTTPS = 3221;

const app = express();

// Middleware pour nanalyser le JSON des requêtes entrantes
app.use(express.json());

// Configuration de l'accès aux fichiers statiques et gestion du MIME type pour le CSS
app.use(express.static(__dirname + '/CerisoNet/dist/ceriso-net/browser/', {
  setHeaders: function (res, path) {
    if (path.endsWith('.css')) {
      res.set('Content-Type', 'text/css');
    }
  }
}));

// Configuration du CORS pour autoriser les requêtes depuis le front
app.use(cors({
    origin: "http://localhost:3222",
    methods: ["GET", "POST", "PUT", "DELETE"],
    credentials: true,
}));

// Chargement des certificats SSL pour la configuration HTTPS
const options = {
    key: fs.readFileSync("private-key.pem"),
    cert: fs.readFileSync("certificate.pem"),
};

// Route principale (renvoi le index.html de l'application)
app.get('/', (req, res) => {
    res.sendFile("index.html", { root: path.join(__dirname, "/CerisoNet/dist/ceriso-net/browser/") });
});

// Route de connexion (TODO: ajouter une véritable authentification)
app.post('/login', (req, res) => {
   console.log(req.body); 
});

// Route si erreur
app.get("/error", (req, res) => {
    res.status(500).json({ message: "Erreur serveur" });
});


// Création du serveur HTTPS et écoute sur le port défini (3221)
https.createServer(options, app).listen(PORT_HTTPS, () => {
    console.log(`Serveur HTTPS en écoute sur https://localhost:${PORT_HTTPS}`);
});
