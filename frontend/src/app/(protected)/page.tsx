import Link from "next/link";

const listings = [
  {
    title: "MacBook Pro 14\"",
    price: "₹70,550",
    condition: "Used - Like New",
    footnote: "Saves 120kg CO2",
    image: "img-card macbook"
  },
  {
    title: "Levi's Denim Jacket",
    price: "₹2,905",
    condition: "Used - Good",
    footnote: "Saves 15kg CO2",
    image: "img-card jacket"
  },
  {
    title: "Psychology Bundle",
    price: "₹3,320",
    condition: "Used - Fair",
    footnote: "Saves 9kg CO2",
    image: "img-card books"
  },
  {
    title: "Nike Air Force 1",
    price: "₹5,395",
    condition: "Used - Like New",
    footnote: "Saves 13kg CO2",
    image: "img-card shoes"
  }
];

export default function HomePage() {
  return (
    <div className="home">
      <main className="page">
        <section className="hero-grid" id="marketplace">
          <div className="hero-copy">
            <span className="pill">The campus circular economy</span>
            <h1>
              Reuse Smart. <span>Buy Better.</span> Build a Sustainable Campus.
            </h1>
            <p>
              Join the student marketplace where sustainability meets
              affordability. Swap, sell, and find pre-loved campus essentials.
            </p>
            <div className="hero-cta">
              <Link className="btn primary" href="/marketplace">
                Explore Items
              </Link>
              <Link className="btn" href="/marketplace/add">
                Sell an Item
              </Link>
            </div>
          </div>
          <div className="hero-media">
            <div className="media-grid">
              <div className="img-card laptop" />
              <div className="img-card lifestyle" />
              <div className="img-card stack" />
              <div className="img-card desk" />
            </div>
          </div>
        </section>

        <section className="stats">
          <div>
            <strong>12,450+</strong>
            <span>Items reused</span>
          </div>
          <div>
            <strong>3.2 Tons</strong>
            <span>CO2 saved</span>
          </div>
          <div>
            <strong>₹69.7L+</strong>
            <span>Student savings</span>
          </div>
          <div>
            <strong>4,800</strong>
            <span>Active eco-warriors</span>
          </div>
        </section>

        <section className="listings" aria-label="Recently listed">
          <div className="section-head">
            <div>
              <span className="section-kicker">Recently listed</span>
              <h2>Campus Favorites</h2>
            </div>
            <a className="link" href="/marketplace">
              View All Marketplace &rarr;
            </a>
          </div>
          <div className="listing-grid">
            {listings.map((item) => (
              <article className="listing-card" key={item.title}>
                <div className={item.image} />
                <div className="badge">{item.condition}</div>
                <div className="listing-body">
                  <h3>{item.title}</h3>
                  <p>
                    {item.price} - {item.footnote}
                  </p>
                </div>
              </article>
            ))}
          </div>
        </section>

        <section className="process" id="tracker">
          <span className="section-kicker">The process</span>
          <h2>Simple. Sustainable. Smart.</h2>
          <p>Three steps to reducing your footprint and upgrading your student life.</p>
          <div className="process-steps">
            <div>
              <div className="step">1</div>
              <h3>List Your Item</h3>
              <p>Snap a photo, set a price, and tag your impact in under a minute.</p>
            </div>
            <div>
              <div className="step">2</div>
              <h3>Connect Safely</h3>
              <p>Chat with verified students from your campus. No external links.</p>
            </div>
            <div>
              <div className="step">3</div>
              <h3>Exchange Locally</h3>
              <p>Meet at a campus hotspot and complete the trade. Easy and fast.</p>
            </div>
          </div>
        </section>

        <section className="why" id="community">
          <div className="why-copy">
            <span className="section-kicker">Why circular?</span>
            <h2>Keeping Value Within Our Community.</h2>
            <p>
              The traditional "take-make-waste" model is broken. ReuseHub keeps
              everyday essentials circulating inside campus, reducing emissions and
              building a stronger local network.
            </p>
            <ul>
              <li>Zero shipping waste</li>
              <li>Student verification</li>
            </ul>
          </div>
          <div className="why-cards">
            <div className="mini-card">
              <h4>Circular Model</h4>
              <p>Reuse items longer and reduce carbon waste.</p>
            </div>
            <div className="photo-card green" />
            <div className="photo-card people" />
            <div className="mini-card">
              <h4>Community Hub</h4>
              <p>Connect students across disciplines for mutual benefit.</p>
            </div>
          </div>
        </section>
      </main>

      <footer className="footer">
        <div>
          <h3>ReuseHub</h3>
          <p>Empowering students to build a more sustainable future.</p>
        </div>
        <div>
          <h4>Platform</h4>
          <a href="/marketplace">Marketplace</a>
          <a href="#tracker">Sustainability Report</a>
          <a href="#community">Ethics Guidelines</a>
        </div>
        <div>
          <h4>Resources</h4>
          <a href="#">Community Forum</a>
          <a href="#">Help Center</a>
        </div>
        <div>
          <h4>Stay connected</h4>
          <div className="social">
            <span>Share</span>
            <span>News</span>
            <span>Media</span>
          </div>
        </div>
      </footer>
    </div>
  );
}
