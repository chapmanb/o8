# o8: genetic changes we can believe in

o8 handles the hard work of organizing and visualizing genetic variants,
providing a web-based platform to interactively explore and analyze human
variation data. It easily interoperates with analysis frameworks like
[Galaxy][7] and [GenomeSpace][8] to supplement existing tools
with a reactive, intuitive way to identify biologically relevant changes.

o8 is easy to setup and run on your own infrastructure, and a
[demonstration server][2] is available to explore the system capabilities.
It is an open source, freely available platform built on top of existing
software:

- [GATK][9]: The Genome Analysis Toolkit from the Broad Institute
- [gemini][10]: a framework for mining genome variation, from the Quinlan lab
- [Variant Effect Predictor][11]: predicts functional
  consequences of variants, from the Ensembl team
- [bcbio.variation][12]: a toolkit to compare and consolidate variation calls from
  multiple sources, developed at Harvard School of Public Health

## Running

The only required pieces of software are Java and the [leiningen][6] build tool.
To run the server:
 
    $ lein run -c config/web-processing.yaml
    
and your site will be available at 'http://localhost:8080`.

To enable functional annotation and querying of biological metrics, install
[gemini][10] and [VEP][11] on the system as well. [CloudBioLinux][12] contains
build instructions to automatically install these tools along with other
biological software.

## Development

### Build HTML and CSS

Setup bundler (rubygem management gem) and use it to get other Ruby dependencies:

    gem install bundler
    bundle install

To build from haml and sass:

    bundle exec guard

to start the Guard watcher (use Chrome Livereload plugin for auto browser
refresh). Hit return to build the HTML and CSS the first time.

### Build javascript

[Leiningen 2][6] required to build Clojure components. To compile JavaScript
from ClojureScript source, watching for changes and automatically recompiling:

    lein with-profile cljs cljsbuild auto
    
### Starting the server

During development:

    lein ring server-headless
    
## License

Funding provided by the [Harvard School of Public Health][4] and [EdgeBio][3]; development
by [Keming Labs][5].

The code is freely available under the [MIT license][l1].

Copyright (c) 2012 Keming Labs, LLC

[1]: https://github.com/chapmanb/bcbio.variation
[2]: http://variantviz.rc.fas.harvard.edu
[3]: http://www.edgebio.com/
[4]: http://compbio.sph.harvard.edu/chb/
[5]: http://keminglabs.com/
[6]: http://leiningen.org/
[7]: https://main.g2.bx.psu.edu/
[8]: http://www.genomespace.org/
[9]: http://www.broadinstitute.org/gatk/
[10]: https://github.com/arq5x/gemini
[11]: http://www.ensembl.org/info/docs/variation/vep/index.html
[12]: http://cloudbiolinux.org

[l1]: http://www.opensource.org/licenses/mit-license.html

