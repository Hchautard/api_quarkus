import express from "express";

const app = express();

app.get("/product", (req, res) => {
    const { nom, stock } = req.query;

    if (!nom || !stock) {
        return res.status(400).json({ message: "Paramètres manquants" });
    }

    res.json({ produit: nom, stock: parseInt(stock) });
});

// GET avec ID
app.get("/produit/:id([0-9]+)", (req, res) => {
    res.json({ message: `Produit avec ID : ${req.params.id}` });
});

// GET avec nom et stock
app.get("/produit/:nom([a-zA-Z]+)/:stock([0-9]+)?", (req, res) => {
    res.json({ produit: req.params.nom, stock: req.params.stock || "Non précisé" });
});

app.post("/product", (req, res) => {
    const { nom, stock } = req.body;

    if (!nom || !stock) {
        return res.status(400).json({ message: "Paramètres manquants" });
    }

    res.json({ produit: nom, stock: parseInt(stock) });
});

// Middleware décrémentant le stock
const decrementeStock1 = (req, res, next) => {
    req.stock = req.stock ? req.stock - 1 : parseInt(req.query.stock) - 1;
    next();
};

const decrementeStock2 = (req, res, next) => {
    req.stock -= 1;
    next();
};

const decrementeStock3 = (req, res, next) => {
    req.stock -= 1;
    res.json({ produit: req.query.nom, stock: req.stock });
};

app.get("/produit-decrement", decrementeStock1, decrementeStock2, decrementeStock3);

export default app;
