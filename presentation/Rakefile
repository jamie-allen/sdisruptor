#                                                                 -*- ruby -*-
# Generate HTML slides, for S5, from Markdown input.
#
# See README.md for pre-requisites.
# ---------------------------------------------------------------------------

require 'rubygems'
require 'rake/clean'
require 'slidedown'
require 'albino'

PRESO_DIR = 'preso'
SLIDE_INPUTS = FileList[File.join(PRESO_DIR, '*.md')]
SASS_DIR = "sass"
DATA_DIR = "data"
CSS_DIR = "."

SASS_FILES = FileList["#{SASS_DIR}/*.scss"]
CSS_OUTPUT_FILES = SASS_FILES.map do |f|
  f.gsub(/^#{SASS_DIR}/, CSS_DIR).gsub(/\.scss$/, '.css')
end

CLEAN << CSS_OUTPUT_FILES
CLEAN << FileList['*.html']
CLEAN << "static"
PWD = File.dirname(__FILE__)

task :default => :slides
task :css => CSS_OUTPUT_FILES
task :run => :css do
  sh "showoff serve"
end

task :slides => ['Rakefile'] + CSS_OUTPUT_FILES + SLIDE_INPUTS do |t|
  sh "showoff static"
  cp "chariot.png", File.join("static", "file")
  cp FileList[File.join(PRESO_DIR, "*.png")], "static"
  fix_static_html(File.join("static", "index.html"))
end

def fix_static_html(path)
  # Not sure why ShowOff does this, but image URLs get preceded with a
  # file: URL.
  require 'tempfile'
  temp = Tempfile.new('static')
  begin
    static_index = File.join("static", "index.html")
    preso_url = "file://" + File.expand_path(PRESO_DIR) + "/"
    File.open(static_index).readlines.each do |line|
      temp.write(line.sub(%r|#{preso_url}|, ""))
    end
    temp.rewind
    File.open(static_index, 'w') do |f|
      f.write temp.read
    end
  ensure
    temp.close
    temp.unlink
  end
end

# ---------------------------------------------------------------------------
# Auto-generate CSS files from any SASS input files.

directory CSS_DIR

# Figure out the name of the SCSS file necessary make a CSS file.
def css_to_scss
  Proc.new {|task| task.sub(/^#{CSS_DIR}/, SASS_DIR).
                        sub(/\.css$/, '.scss')}
end

rule %r{^#{CSS_DIR}/.*\.css$} => [css_to_scss, 'Rakefile'] + SASS_FILES do |t|
  require 'sass'
  mkdir_p CSS_DIR
  puts("#{t.source} -> #{t.name}")
  Dir.chdir('sass') do
    sass_input = File.basename(t.source)
    engine = Sass::Engine.new(File.open(sass_input).readlines.join(''),
                              :syntax => :scss)
    out = File.open(File.join('..', t.name), 'w')
    out.write("/* AUTOMATICALLY GENERATED FROM #{t.source} on #{Time.now} */\n")
    out.write(engine.render)
    # Force close, to force flush BEFORE running other tasks.
    out.close
  end
end

