"""
Embedding Generation Script for Hayat AI
Generates real vector embeddings using multilingual-e5-small model.

This script reads medical protocols from text files or PDFs, chunks them,
and generates embeddings using the multilingual-e5-small model with proper
prefixes ('passage:' for documents, 'query:' for search queries).

Requirements:
    pip install sentence-transformers onnxruntime numpy pypdf2
"""

import sqlite3
import struct
from pathlib import Path
from typing import List, Dict, Tuple
import numpy as np

try:
    from sentence_transformers import SentenceTransformer
    SENTENCE_TRANSFORMERS_AVAILABLE = True
except ImportError:
    SENTENCE_TRANSFORMERS_AVAILABLE = False
    print("⚠️  Warning: sentence-transformers not installed. Install with: pip install sentence-transformers")

# E5 model requires specific prefixes
E5_PASSAGE_PREFIX = "passage: "
E5_QUERY_PREFIX = "query: "

# Chunking configuration
CHUNK_SIZE = 200  # words per chunk
CHUNK_OVERLAP = 50  # words overlap between chunks


def load_medical_protocols_from_pdf(pdf_path: str) -> List[str]:
    """Extract text from PDF medical manuals."""
    try:
        from PyPDF2 import PdfReader
        reader = PdfReader(pdf_path)
        text = ""
        for page in reader.pages:
            text += page.extract_text()
        return [text]
    except ImportError:
        print("⚠️  PyPDF2 not installed. Install with: pip install pypdf2")
        return []
    except Exception as e:
        print(f"Error reading PDF: {e}")
        return []


def chunk_text(text: str, chunk_size: int = CHUNK_SIZE, overlap: int = CHUNK_OVERLAP) -> List[str]:
    """
    Split text into overlapping chunks for better context preservation.
    
    Args:
        text: Input text to chunk
        chunk_size: Number of words per chunk
        overlap: Number of overlapping words between chunks
    
    Returns:
        List of text chunks
    """
    words = text.split()
    chunks = []
    
    if len(words) <= chunk_size:
        return [text.strip()]
    
    start = 0
    while start < len(words):
        end = start + chunk_size
        chunk = ' '.join(words[start:end])
        chunks.append(chunk.strip())
        
        if end >= len(words):
            break
        
        start = end - overlap
    
    return chunks


def generate_embeddings_with_prefix(
    texts: List[str], 
    model: SentenceTransformer, 
    prefix: str = E5_PASSAGE_PREFIX
) -> List[np.ndarray]:
    """
    Generate embeddings using multilingual-e5-small with proper prefix.
    
    Args:
        texts: List of texts to embed
        model: SentenceTransformer model instance
        prefix: Prefix to add ('passage:' or 'query:')
    
    Returns:
        List of embedding vectors
    """
    if not texts:
        return []
    
    # Add prefix to each text
    prefixed_texts = [prefix + text for text in texts]
    
    # Generate embeddings
    embeddings = model.encode(
        prefixed_texts,
        batch_size=8,
        show_progress_bar=True,
        convert_to_numpy=True,
        normalize_embeddings=True  # L2 normalization for cosine similarity
    )
    
    return embeddings.tolist()


def create_database_with_embeddings(
    db_path: str,
    protocols: List[Dict],
    model: SentenceTransformer
) -> int:
    """
    Create SQLite database with medical protocols and their embeddings.
    
    Args:
        db_path: Path to output database file
        protocols: List of protocol dictionaries with 'title' and 'content'
        model: SentenceTransformer model for embedding generation
    
    Returns:
        Number of chunks inserted
    """
    # Remove existing database
    db_file = Path(db_path)
    if db_file.exists():
        db_file.unlink()
    
    conn = sqlite3.connect(db_path)
    cursor = conn.cursor()
    
    # Create table with FLOAT32 blob for embeddings
    cursor.execute('''
        CREATE TABLE IF NOT EXISTS medical_chunks (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            protocol_title TEXT NOT NULL,
            content TEXT NOT NULL,
            embedding BLOB NOT NULL
        )
    ''')
    
    total_chunks = 0
    
    for protocol in protocols:
        # Chunk the content
        chunks = chunk_text(protocol['content'])
        
        # Generate embeddings with passage prefix
        embeddings = generate_embeddings_with_prefix(
            chunks, 
            model, 
            prefix=E5_PASSAGE_PREFIX
        )
        
        # Insert each chunk with its embedding
        for i, chunk in enumerate(chunks):
            embedding_blob = struct.pack(f'{len(embeddings[i])}f', *embeddings[i])
            
            cursor.execute(
                'INSERT INTO medical_chunks (protocol_title, content, embedding) VALUES (?, ?, ?)',
                (protocol['title'], chunk.strip(), embedding_blob)
            )
            total_chunks += 1
    
    conn.commit()
    conn.close()
    
    return total_chunks


def test_query_embedding(query: str, model: SentenceTransformer) -> np.ndarray:
    """
    Generate embedding for a search query with proper prefix.
    
    Args:
        query: User's search query
        model: SentenceTransformer model instance
    
    Returns:
        Query embedding vector
    """
    prefixed_query = E5_QUERY_PREFIX + query
    embedding = model.encode(
        [prefixed_query],
        convert_to_numpy=True,
        normalize_embeddings=True
    )
    return embedding[0]


def main():
    """Main function to generate embeddings for medical protocols."""
    
    if not SENTENCE_TRANSFORMERS_AVAILABLE:
        print("\n❌ Cannot generate embeddings: sentence-transformers not installed")
        print("Install with: pip install sentence-transformers")
        return
    
    print("🔽 Loading multilingual-e5-small model...")
    try:
        model = SentenceTransformer('intfloat/multilingual-e5-small')
        print("✅ Model loaded successfully")
    except Exception as e:
        print(f"❌ Error loading model: {e}")
        return
    
    # Sample medical protocols (in production, load from PDFs)
    medical_protocols = [
        {
            "title": "Severe Bleeding Control",
            "content": """
                Step 1: Apply direct pressure to the wound with a clean cloth or bandage.
                Press firmly with both hands if possible. Maintain continuous pressure.
                Step 2: If bleeding continues through the first dressing, do not remove it.
                Add additional layers on top and press harder.
                Step 3: For life-threatening limb bleeding, apply a tourniquet 2-3 inches 
                above the wound, between the wound and the heart. Never place over joints.
                Step 4: Tighten the tourniquet until bleeding stops completely.
                Step 5: Note the exact time of tourniquet application on the patient or device.
                Step 6: Do not remove or loosen the tourniquet once applied.
                Step 7: Seek immediate medical help. Tourniquets are for emergency use only.
                Warning: Tourniquets can cause tissue damage if left on too long.
                Only use for severe, life-threatening bleeding that cannot be controlled otherwise.
            """
        },
        {
            "title": "Burn Treatment",
            "content": """
                Step 1: Immediately remove the person from the source of the burn.
                Extinguish any flames by rolling the person on the ground.
                Step 2: Cool the burn with cool (not ice-cold) running water for 10-20 minutes.
                This reduces pain, swelling, and tissue damage.
                Step 3: Gently remove any jewelry, watches, or tight clothing near the burn
                before swelling occurs. Do not remove anything stuck to the burn.
                Step 4: Cover the burn with a sterile, non-stick bandage or clean cloth.
                Use cling film or plastic wrap for large burns.
                Step 5: Do not pop blisters as they protect against infection.
                Step 6: Do not apply ice directly, butter, oil, toothpaste, or ointments.
                These can worsen the injury and increase infection risk.
                Step 7: For severe burns (large area, face, hands, feet, genitals, major joints),
                or chemical/electrical burns, seek immediate medical attention.
                Step 8: Monitor for signs of shock and keep the person warm.
            """
        },
        {
            "title": "Fracture Stabilization",
            "content": """
                Step 1: Check the person's breathing and level of consciousness.
                Call for emergency medical help immediately.
                Step 2: Stop any bleeding by applying gentle pressure with a clean cloth.
                Do not press directly on protruding bones.
                Step 3: Immobilize the injured area using a splint.
                Use rigid materials like wood, cardboard, or rolled newspapers.
                Pad the splint with cloth for comfort.
                Step 4: Secure the splint with bandages or cloth strips above and below
                the fracture site. Do not tie directly over the fracture.
                Step 5: Apply ice packs wrapped in cloth to reduce swelling and pain.
                Apply for 15-20 minutes at a time.
                Step 6: Keep the person still and comfortable.
                Do not allow them to move the injured area.
                Step 7: Monitor for signs of shock (pale skin, rapid pulse, confusion).
                Step 8: Do not try to realign or push back protruding bones.
                This can cause severe damage to nerves and blood vessels.
                Step 9: For suspected spinal injuries, do not move the person unless
                there is immediate danger. Keep head and neck aligned.
            """
        }
    ]
    
    # Create database with embeddings
    assets_dir = Path("/workspace/app/src/main/assets")
    assets_dir.mkdir(parents=True, exist_ok=True)
    
    db_path = assets_dir / "medical.db"
    print(f"\n📊 Creating English medical database at: {db_path}")
    
    count = create_database_with_embeddings(str(db_path), medical_protocols, model)
    
    print(f"✅ Database created successfully")
    print(f"   - Total chunks: {count}")
    print(f"   - Embedding dimension: 384")
    print(f"   - File size: {db_path.stat().st_size / 1024:.2f} KB")
    
    # Test query embedding
    print("\n🧪 Testing query embedding generation...")
    test_query = "How do I stop severe bleeding?"
    query_embedding = test_query_embedding(test_query, model)
    print(f"✅ Query embedding generated: shape={query_embedding.shape}, dtype={query_embedding.dtype}")
    
    print("\n💡 Note: Remember to use 'query:' prefix in RagEngine.kt for user searches")
    print("   Example: val processedQuery = \"query: \" + userInput")


if __name__ == "__main__":
    main()
