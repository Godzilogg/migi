require 'nokogiri'
require 'pry'
require 'super/color'

html_doc = Nokogiri::XML("<abc><body><h1>Mr. Belvedere Fan Club</h1></body></abc>")
puts html_doc.elements[0].name.yellow
puts html_doc.elements[0].children

binding.pry

puts "hello world"
