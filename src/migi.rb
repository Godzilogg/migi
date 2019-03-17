#
#
# Run with: ruby src/migi.rb -i input_file -m migration.xml
# Run with: ruby src/migi.rb -d ./directory -m migration.xml
# ruby src/migi.rb -i test/super_file.bin -m test/new.xml
#

require 'nokogiri'
require 'pry'
require 'super/color'

# All of our data types
require './src/data_types/*'
require './src/utils/*'

args_helper = Migi::ArgsHelper.new

# If we have a single file
if args_helper.input_filename
  Migi::DataMigrator.migrate_file(args_helper.input_filename, args_helper.migration_filepath)
# If we are given a directory of files
elsif args_helper.input_files_directory
  Migi::DataMigrator.migrate_directory(args_helper.input_files_directory, args_helper.migration_filepath)
end


# TODO: cleanup this mess:
html_doc = Nokogiri::XML("<abc><body><h1>Mr. Belvedere Fan Club</h1></body></abc>")
puts html_doc.elements[0].name.yellow
puts html_doc.elements[0].children

puts "hello world"
